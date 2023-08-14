/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.codelists.jdbc.SQLCodeLists
import sirius.biz.tenants.TenantsHelper
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part

@ExtendWith(SiriusExtension::class)
class SQLMultiLanguageStringPropertyTest {

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA

        @Part
        @JvmStatic
        private lateinit var codeLists: SQLCodeLists
    }

    @Test
    fun `store retrieve and validate`() {
        TenantsHelper.installTestTenant()
        val entity = SQLMultiLanguageStringEntity()
        entity.multiLangText.addText("de", "Schmetterling")
        entity.multiLangText.addText("en", "Butterfly")
        oma.update(entity)
        val output = oma.refreshOrFail(entity)

        assertEquals(2, output.multiLangText.size())
        assertTrue { output.multiLangText.hasText("de") }
        assertFalse { output.multiLangText.hasText("fr") }
        assertEquals("Schmetterling", output.multiLangText.fetchText("de"))
        assertNull(output.multiLangText.fetchText("fr"))
        assertEquals("Schmetterling", output.multiLangText.fetchText("de", "en"))
        assertEquals("Butterfly", output.multiLangText.fetchText("fr", "en"))
        assertNull(output.multiLangText.fetchText("fr", "es"))
        assertEquals(Optional.of("Schmetterling"), output.multiLangText.getText("de"))
        assertTrue(output.multiLangText.getText("fr").isEmpty)

        CallContext.getCurrent().language = "en"

        assertEquals("Butterfly", output.multiLangText.fetchText())
        assertEquals(Optional.of("Butterfly"), output.multiLangText.text)

        CallContext.getCurrent().language = "fr"

        assertNull(output.multiLangText.fetchText())
        assertTrue(output.multiLangText.text.isEmpty)
    }
}
