/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import org.junit.jupiter.api.Test
import sirius.biz.saml.SamlResponse
import sirius.kernel.commons.MultiMap
import kotlin.test.assertFalse
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
}
