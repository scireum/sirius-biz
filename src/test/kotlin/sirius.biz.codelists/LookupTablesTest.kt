/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(SiriusExtension::class)
class LookupTablesTest {

    companion object {
        private val lookupTables = LookupTables()
    }

    @Test
    fun `TestExtensionLookupTable creation works`() {
        val table = lookupTables.fetchTable("test-extension-table")
        assertEquals(1, table.count())
        assertTrue(table.normalize("test").isPresent)

        // reading translations from NLS keys works
        assertEquals("Name", table.resolveName("test").get())
        assertEquals("Uzvārds", table.resolveName("test", "lv").get())

        // reading translations directly from the config works
        assertEquals("Die beste Beschreibung", table.resolveDescription("test").get())
        assertEquals("Den bästa beskrivningen", table.resolveDescription("test", "sv").get())
    }

    @Test
    fun `TestJsonLookupTable creation works`() {
        val table = lookupTables.fetchTable("test-json-table")
        assertEquals(1, table.count())
        assertTrue(table.normalize("test").isPresent)
        assertEquals("Der beste Name", table.resolveName("test").get())
        assertEquals("Die beste Beschreibung", table.resolveDescription("test").get())
        assertEquals("Det bästa namnet", table.resolveName("test", "sv").get())
        assertEquals("Den bästa beskrivningen", table.resolveDescription("test", "sv").get())
    }
}
