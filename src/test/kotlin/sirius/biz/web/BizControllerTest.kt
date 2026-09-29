/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.web

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the [BizController.isLocalUrl] check used to prevent open redirects.
 */
class BizControllerTest {

    @ParameterizedTest
    @CsvSource(
            textBlock = """
            /catalog/123; true
            /catalog/123?foo=bar; true
            https://www.mydomain.stuff/assortment/1/product/2; true
            http://www.mydomain.stuff/; true
            https://www.mydomain.stuff; false
            https://www.mydomain.stuff.evil.stuff/; false
            https://www.mydomain.stuff@evil.stuff/; false
            https://evil.stuff/; false
            //evil.stuff/; false
            /\evil.stuff/; false
            javascript:alert(1); false
            https:evil.stuff; false
            catalog/123; false""", delimiter = ';'
    )
    fun testIsLocalUrl(url: String, expected: Boolean) {
        assertEquals(expected, BizController.isLocalUrl(url, "https://www.mydomain.stuff"))
    }

    @Test
    fun testIsLocalUrlHandlesEdgeCases() {
        assertFalse(BizController.isLocalUrl(null, "https://www.mydomain.stuff"))
        assertFalse(BizController.isLocalUrl("/\t/evil.stuff", "https://www.mydomain.stuff"))
        assertFalse(BizController.isLocalUrl("https://www.mydomain.stuff/", null))
        assertTrue(BizController.isLocalUrl("/catalog/123", null))
    }
}
