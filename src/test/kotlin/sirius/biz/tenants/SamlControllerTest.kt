/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.saml.SamlResponse
import sirius.biz.tenants.jdbc.SQLTenant
import sirius.biz.tenants.jdbc.SQLUserAccount
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.MultiMap
import sirius.kernel.health.HandledException
import sirius.web.http.TestRequest
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the [SamlController].
 */
@ExtendWith(SiriusExtension::class)
class SamlControllerTest {

    @Test
    fun `SAML controller fingerprint check normalizes configured fingerprints`() {
        val response = samlResponse()

        assertTrue(
            SamlController.isTrustedFingerprint(
                "71038506714cb316a8cb6500b26551b1c29375ce, C5:FB:EB:48:78:60:C8:E3:CB:E5:64:09:61:70:21:D0:1B:E3:71:FF",
                response
            )
        )
        assertFalse(SamlController.isTrustedFingerprint("71038506714cb316a8cb6500b26551b1c29375ce", response))
        assertFalse(SamlController.isTrustedFingerprint("", response))
    }

    @Test
    fun `SAML controller rejects untrusted fingerprint before user creation`() {
        val trustedTenant = samlTenant("C5:FB:EB:48:78:60:C8:E3:CB:E5:64:09:61:70:21:D0:1B:E3:71:FF")
        val untrustedTenant = samlTenant("71038506714cb316a8cb6500b26551b1c29375ce")
        val response = samlResponse()

        assertSame(trustedTenant, TestSamlController(listOf(trustedTenant)).findTrustedTenant(response))
        assertThrows<HandledException> {
            TestSamlController(listOf(untrustedTenant)).findTrustedTenant(response)
        }
    }

    @Test
    fun `SAML controller picks the tenant trusting the fingerprint among tenants sharing the issuer`() {
        val firstTenant = samlTenant(OTHER_FINGERPRINT, requestIssuer = "app-first")
        val secondTenant = samlTenant(FINGERPRINT, requestIssuer = "app-second")
        val controller = TestSamlController(listOf(firstTenant, secondTenant))

        assertSame(secondTenant, controller.findTrustedTenant(samlResponse()))
        assertSame(firstTenant, controller.findTrustedTenant(samlResponse(fingerprint = OTHER_FINGERPRINT)))
    }

    @Test
    fun `SAML controller picks the tenant matching the audience among tenants sharing issuer and fingerprint`() {
        val firstTenant = samlTenant(FINGERPRINT, requestIssuer = "app-first")
        val secondTenant = samlTenant(FINGERPRINT, requestIssuer = "app-second")
        val controller = TestSamlController(listOf(firstTenant, secondTenant))

        assertSame(secondTenant, controller.findTrustedTenant(samlResponse(audience = "app-second")))
        assertSame(firstTenant, controller.findTrustedTenant(samlResponse(audience = "app-first")))
    }

    @Test
    fun `SAML controller falls back to the first trusted tenant if the audience does not decide`() {
        val firstTenant = samlTenant(FINGERPRINT, requestIssuer = "app-first")
        val secondTenant = samlTenant(FINGERPRINT, requestIssuer = "app-second")
        val controller = TestSamlController(listOf(firstTenant, secondTenant))

        assertSame(firstTenant, controller.findTrustedTenant(samlResponse()))
        assertSame(firstTenant, controller.findTrustedTenant(samlResponse(audience = "app-unknown")))
    }

    @Test
    fun `SAML controller ignores trusted tenants with another issuer`() {
        val foreignTenant = samlTenant(FINGERPRINT, issuer = "other-issuer", requestIssuer = "app-second")
        val tenant = samlTenant(FINGERPRINT, requestIssuer = "app-first")
        val controller = TestSamlController(listOf(foreignTenant, tenant))

        assertSame(tenant, controller.findTrustedTenant(samlResponse(audience = "app-second")))
    }

    @Test
    fun `SAML controller rejects responses without matching issuer or fingerprint`() {
        val controller = TestSamlController(
            listOf(
                samlTenant(OTHER_FINGERPRINT, requestIssuer = "app-first"),
                samlTenant(OTHER_FINGERPRINT, requestIssuer = "app-second")
            )
        )

        assertContains(
            assertThrows<HandledException> {
                controller.findTrustedTenant(samlResponse(audience = "app-second"))
            }.message.orEmpty(),
            "Fingerprint mismatch"
        )
        assertContains(
            assertThrows<HandledException> {
                controller.findTrustedTenant(samlResponse(issuer = "unknown-issuer"))
            }.message.orEmpty(),
            "No matching tenant found for issuer: unknown-issuer"
        )
    }

    @Test
    fun `SAML controller starts login for samlTenantId parameter`() {
        val tenant = samlTenant("C5:FB:EB:48:78:60:C8:E3:CB:E5:64:09:61:70:21:D0:1B:E3:71:FF")
        tenant.setId(4711)
        val controller = TestSamlController(listOf(tenant))

        assertTrue(
            controller.tryStartTenantSamlLogin(
                TestRequest.GET("/admin?samlTenantId=4711"),
                "/admin"
            )
        )
        assertFalse(
            controller.tryStartTenantSamlLogin(
                TestRequest.GET("/admin?samlTenantId=4712"),
                "/admin"
            )
        )
    }

    private fun samlTenant(
        fingerprint: String,
        issuer: String = "issuer",
        requestIssuer: String = "request-issuer"
    ): SQLTenant {
        val tenant = SQLTenant()
        tenant.tenantData.samlIssuerName = issuer
        tenant.tenantData.samlIssuerUrl = "https://sso.example.test/login"
        tenant.tenantData.samlRequestIssuerName = requestIssuer
        tenant.tenantData.samlFingerprint = fingerprint
        return tenant
    }

    private fun samlResponse(
        issuer: String = "issuer",
        audience: String? = null,
        fingerprint: String = "c5fbeb487860c8e3cbe56409617021d01be371ff"
    ): SamlResponse {
        return SamlResponse(issuer, audience, fingerprint, "name-id", MultiMap.create())
    }

    companion object {
        private const val FINGERPRINT = "C5:FB:EB:48:78:60:C8:E3:CB:E5:64:09:61:70:21:D0:1B:E3:71:FF"
        private const val OTHER_FINGERPRINT = "71038506714cb316a8cb6500b26551b1c29375ce"
    }

    private class TestSamlController(private val tenants: List<SQLTenant>) :
        SamlController<Long, SQLTenant, SQLUserAccount>() {

        override fun querySAMLTenants(): List<SQLTenant> {
            return tenants
        }
    }
}
