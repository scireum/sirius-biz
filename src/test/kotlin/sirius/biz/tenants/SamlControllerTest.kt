/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sirius.biz.saml.SamlResponse
import sirius.biz.tenants.jdbc.SQLTenant
import sirius.biz.tenants.jdbc.SQLUserAccount
import sirius.kernel.commons.MultiMap
import sirius.kernel.health.HandledException
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SamlControllerTest {

    @Test
    fun `SAML controller fingerprint check normalizes configured fingerprints`() {
        val response = SamlResponse(
            "issuer",
            "c5fbeb487860c8e3cbe56409617021d01be371ff",
            "name-id",
            MultiMap.create()
        )

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

    private fun samlTenant(fingerprint: String): SQLTenant {
        val tenant = SQLTenant()
        tenant.tenantData.samlIssuerName = "issuer"
        tenant.tenantData.samlFingerprint = fingerprint
        return tenant
    }

    private fun samlResponse(): SamlResponse {
        return SamlResponse(
            "issuer",
            "c5fbeb487860c8e3cbe56409617021d01be371ff",
            "name-id",
            MultiMap.create()
        )
    }

    private class TestSamlController(private val tenants: List<SQLTenant>) :
        SamlController<Long, SQLTenant, SQLUserAccount>() {

        override fun querySAMLTenants(): List<SQLTenant> {
            return tenants
        }
    }
}
