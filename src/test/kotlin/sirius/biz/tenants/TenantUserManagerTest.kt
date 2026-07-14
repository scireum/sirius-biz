/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.isenguard.Isenguard
import sirius.biz.isenguard.RateLimitingInfo
import sirius.biz.tenants.jdbc.SQLTenantUserManager
import sirius.db.jdbc.OMA
import sirius.kernel.Sirius
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import sirius.web.http.TestRequest
import sirius.web.http.WebContext
import sirius.web.security.UserContext
import java.net.InetAddress
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [TenantUserManager].
 */
@ExtendWith(SiriusExtension::class)
class TenantUserManagerTest {

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA

        @Part
        @JvmStatic
        private lateinit var isenguard: Isenguard

        @BeforeAll
        @JvmStatic
        fun setup() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }

    @Test
    fun `Only failed password logins count towards rate limit`() {
        TenantsHelper.getTestUser()

        val userManager = UserContext.get().userManager as SQLTenantUserManager
        val webContext = TestRequest.GET("/").withRemoteIp(InetAddress.getByName("127.0.0.41"))

        assertNotNull(userManager.findUserByCredentials(webContext, "test", "test"))
        assertNull(userManager.findUserByCredentials(webContext, "test", "wrong-password-1"))
        assertNull(userManager.findUserByCredentials(webContext, "test", "wrong-password-2"))

        assertThrows<HandledException> {
            userManager.findUserByCredentials(webContext, "test", "wrong-password-3")
        }
    }

    @Test
    fun `Password login captcha is only required for login form requests`() {
        val failedAttemptsBeforeCaptcha =
            Sirius.getSettings().get("security.passwordLoginCaptcha.failedAttemptsBeforeCaptcha").asInt(4)
        val remoteIp = InetAddress.getByName("127.0.0.42")
        val loginViaParameter = TestRequest.GET("/?user=system&password=system").withRemoteIp(remoteIp)
        val loginViaForm = TestRequest.POST("/")
            .withParameter("user", "system")
            .withParameter("password", "system")
            .withParameter("passwordLoginForm", true)
            .withRemoteIp(remoteIp)

        repeat(failedAttemptsBeforeCaptcha) {
            isenguard.registerCallAndCheckRateLimitReached(
                loginViaForm.getRemoteIP().hostAddress,
                TenantUserManager.SECURITY_RATE_LIMIT_REALM,
                failedAttemptsBeforeCaptcha + 1
            ) { RateLimitingInfo.fromWebContext(loginViaForm, null) }
        }
        assertFalse(TenantUserManager.isPasswordLoginCaptchaRequired(loginViaParameter))
        assertTrue(TenantUserManager.isPasswordLoginCaptchaRequired(loginViaForm))
    }

    private fun <T : WebContext> T.withRemoteIp(remoteIp: InetAddress): T {
        WebContext::class.java.getDeclaredField("remoteIp").apply {
            isAccessible = true
            set(this@withRemoteIp, remoteIp)
        }
        return this
    }
}
