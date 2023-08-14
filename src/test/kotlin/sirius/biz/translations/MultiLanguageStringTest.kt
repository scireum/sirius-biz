/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension

@ExtendWith(SiriusExtension::class)
class MultiLanguageStringTest {

    @Test
    fun `adding a text works`() {
        val mls = MultiLanguageString()
        mls.addText("en", "adding text works")
        assertEquals("adding text works", mls.fetchText("en"))
    }

    @Test
    fun `adding a text with null as language key throws IllegalArgumentException`() {
        val mls = MultiLanguageString()
        assertThrows<IllegalArgumentException> {
            mls.addText(null, "null key")
        }
    }

    @Test
    fun `adding a text with an empty String as language key throws IllegalArgumentException`() {
        val mls = MultiLanguageString()
        assertThrows<IllegalArgumentException> {
            mls.addText("", "empty key")
        }
    }

    @Test
    fun `adding a map of values with valid language keys works`() {
        val mls = MultiLanguageString()
        mls.addText("en", "some text")
        mls.addText("de", "irgendein text")
        val map = hashMapOf<String, String?>()
        map["fr"] = "en français"
        map["sv"] = "på svensk"
        mls.setData(map)
        assertEquals(2, mls.data().keys.size)
        assertContains(mls.data().keys, "fr")
        assertContains(mls.data().keys, "sv")
    }

    @Test
    fun `adding a map of values with null as language key throws IllegalArgumentException`() {
        val mls = MultiLanguageString()
        mls.addText("en", "some text")
        mls.addText("de", "irgendein text")
        val map = hashMapOf<String?, String?>()
        map["en"] = "some text"
        map[null] = "null key"
        assertThrows<IllegalArgumentException> {
            mls.setData(map)
        }
    }

    @Test
    fun `adding a map of values with an empty String as language key throws IllegalArgumentException`() {
        val mls = MultiLanguageString()
        mls.addText("en", "some text")
        mls.addText("de", "irgendein text")
        val map = hashMapOf<String, String>()
        map["en"] = "some text"
        map[""] = "empty key"
        assertThrows<IllegalArgumentException> {
            mls.setData(map)
        }
    }
}
