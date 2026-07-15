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
import sirius.biz.tenants.jdbc.SQLTenantUserManager
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import sirius.web.http.WebContext
import sirius.web.security.UserContext
import java.time.Duration
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests the [TenantUserManager].
 */
@ExtendWith(SiriusExtension::class)
class TenantUserManagerTest {

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA

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
        val webContext = WebContext.getCurrent()

        assertNotNull(userManager.findUserByCredentials(webContext, "test", "test"))
        assertNull(userManager.findUserByCredentials(webContext, "test", "wrong-password-1"))
        assertNull(userManager.findUserByCredentials(webContext, "test", "wrong-password-2"))

        assertThrows<HandledException> {
            userManager.findUserByCredentials(webContext, "test", "wrong-password-3")
        }
    }
}
