/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.tenants.TenantsHelper
import sirius.db.es.Elastic
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part

@ExtendWith(SiriusExtension::class)
class ESMultiLanguageStringPropertyTest {

    companion object {
        @Part
        @JvmStatic
        private lateinit var elastic: Elastic
    }

    @Test
    fun `reading and writing works`() {
        TenantsHelper.installTestTenant()
        val test = ESMultiLanguageStringEntity()
        test.multiLanguage.put("de", "Das ist ein Test").put("en", "This is a test")
        elastic.update(test)
        var resolved = elastic.refreshOrFail(test)

        assertEquals(2, resolved.multiLanguage.size())
        assertEquals("Das ist ein Test", resolved.multiLanguage.getText("de").get())
        assertEquals("This is a test", resolved.multiLanguage.getText("en").get())

        resolved.multiLanguage.remove("de")
        elastic.update(resolved)
        resolved = elastic.refreshOrFail(test)

        assertEquals(1, resolved.multiLanguage.size())
        assertFalse { resolved.multiLanguage.data().containsValue("Das ist ein Test") }
        assertEquals("This is a test", resolved.multiLanguage.getText("en").get())
    }
}
