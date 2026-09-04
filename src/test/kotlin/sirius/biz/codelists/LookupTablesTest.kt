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
import sirius.kernel.commons.Json
import tools.jackson.databind.node.ObjectNode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(SiriusExtension::class)
class LookupTablesTest {

    companion object {
        private val lookupTables = LookupTables()
    }

    /**
     * Receives the whole entry as JSON, which is what a lookup table hands to a payload type.
     */
    class TestPayload(val data: ObjectNode)

    @Test
    fun `ConfigLookupTable creation works`() {
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
    fun `ConfigLookupTable resolves an object from its data`() {
        val table = lookupTables.fetchTable("test-payload-table")

        val payload = table.fetchObject(TestPayload::class.java, "first")

        assertTrue(payload.isPresent)
        assertEquals("First", payload.get().data.path("name").asString(""))
        assertEquals("kg", payload.get().data.path("unit").asString(""))
        assertEquals("3", payload.get().data.path("limits").path("max").asString(""))

        val tags = Json.getArray(payload.get().data, "tags")
        assertEquals(2, tags.size())
        assertEquals("alpha", tags.path(0).asString(""))
        assertEquals("beta", tags.path(1).asString(""))
    }

    @Test
    fun `ConfigLookupTable reports an unknown code as empty`() {
        val table = lookupTables.fetchTable("test-payload-table")

        assertFalse(table.fetchObject(TestPayload::class.java, "does-not-exist").isPresent)
    }
}
