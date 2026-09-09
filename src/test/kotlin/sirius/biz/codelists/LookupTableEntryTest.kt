/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists

import org.junit.jupiter.api.Test
import sirius.kernel.commons.Json
import tools.jackson.databind.node.ObjectNode
import kotlin.test.assertEquals

/**
 * Pins the YAML rendering of [LookupTableEntry.getSource].
 *
 * We use SnakeYAML in three places ([LookupTableEntry.withSource] plus
 * [sirius.biz.jupiter.JupiterSync] and [sirius.biz.jupiter.JupiterYamlDataProvider]), and all three only ever
 * emit YAML - we never parse it back. A version bump can therefore only regress the emitted formatting, which
 * no other test in the suite would notice. This one does, at least for the single call site which is reachable
 * without a running Jupiter instance.
 *
 * These are deliberately characterization tests: they compare the full output verbatim and are split by the
 * emitter property they pin, so that a failure names the property which moved. The expected values were
 * captured against SnakeYAML 2.7. When one of them goes red after a bump, do not just paste in the new value -
 * open the source panel of the lookup table picker, judge whether the new rendering is still acceptable, and
 * only then update the literal.
 */
class LookupTableEntryTest {

    @Test
    fun `source is emitted as block style YAML preserving the order of the payload`() {
        val entry = entryWithSource(
            """
            {"code":"DE","name":"Deutschland","population":84000000,"euMember":true,
             "successor":null,"codes":{"iso3":"DEU","numeric":276},"languages":["de","en"]}
            """
        )

        val expected = """
            code: DE
            name: Deutschland
            population: 84000000
            euMember: true
            successor: null
            codes:
              iso3: DEU
              numeric: 276
            languages:
            - de
            - en
        """.trimIndent() + "\n"

        assertEquals(expected, entry.source)
    }

    @Test
    fun `source folds long scalars at the default width of eighty characters`() {
        val entry = entryWithSource(
            """
            {"description":"Die Bundesrepublik Deutschland ist ein Staat in Mitteleuropa und besteht aus sechzehn Laendern."}
            """
        )

        val expected = """
            description: Die Bundesrepublik Deutschland ist ein Staat in Mitteleuropa und besteht
              aus sechzehn Laendern.
        """.trimIndent() + "\n"

        assertEquals(expected, entry.source)
    }

    @Test
    fun `source quotes scalars which would otherwise resolve to another type`() {
        val entry = entryWithSource(
            """
            {"zeroPadded":"007","boolLike":"yes","nullLike":"null","empty":"","colon":"a: b","hash":"a #b"}
            """
        )

        val expected = """
            zeroPadded: '007'
            boolLike: 'yes'
            nullLike: 'null'
            empty: ''
            colon: 'a: b'
            hash: 'a #b'
        """.trimIndent() + "\n"

        assertEquals(expected, entry.source)
    }

    @Test
    fun `source renders empty collections in pretty flow style`() {
        val entry = entryWithSource("""{"emptyObject":{},"emptyArray":[]}""")

        // This is the only rendering which observes options.setPrettyFlow(true) - without it, SnakeYAML would
        // emit the far tidier "{}" and "[]" instead.
        val expected = """
            emptyObject: {
              }
            emptyArray: [
              ]
        """.trimIndent() + "\n"

        assertEquals(expected, entry.source)
    }

    @Test
    fun `source emits non-ASCII characters literally instead of escaping them`() {
        val entry = entryWithSource("""{"note":"Grüße aus Remshalden – 100 % Erfolg"}""")

        assertEquals("note: Grüße aus Remshalden – 100 % Erfolg\n", entry.source)
    }

    /**
     * Creates an entry whose source is derived from the given JSON.
     *
     * The JSON is parsed rather than assembled via the typed setters of [ObjectNode], as this is what the only
     * production caller ([IDBLookupTable]) does - it hands over the parsed source column of an IDB row. Parsing
     * also determines the numeric node types, which in turn decide how the values are emitted.
     */
    private fun entryWithSource(json: String): LookupTableEntry {
        val source: ObjectNode = Json.parseObject(json.trimIndent())

        return LookupTableEntry("DE", "Deutschland", null).withSource(source)
    }
}
