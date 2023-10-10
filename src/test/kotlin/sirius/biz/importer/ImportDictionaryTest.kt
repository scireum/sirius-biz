/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.importer.format.FieldDefinition
import sirius.biz.importer.format.ImportDictionary
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Tuple
import sirius.kernel.commons.Values
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests the [ImportDictionary] class.
 */
@ExtendWith(SiriusExtension::class)
class ImportDictionaryTest {

    @Test
    fun `detectHeaderProblems detects a skipped column`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C", "D", "E"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(Values.of(listOf("A", "C", "D", "E")), { problem, _ -> problems.add(problem) }, true)
        assertEquals("Die Spalte 2 ('B') fehlt.", problems[0])
        assertEquals(1, problems.size)
    }

    @Test
    fun `detectHeaderProblems detects two skipped columns`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C", "D", "E"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(Values.of(listOf("A", "D", "E")), { problem, _ -> problems.add(problem) }, true)
        assertEquals("Die Spalte 2 ('B') fehlt.", problems[0])
        assertEquals("Die Spalte 3 ('C') fehlt.", problems[1])
        assertEquals(2, problems.size)
    }

    @Test
    fun `detectHeaderProblems detects a superfluous column`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(Values.of(listOf("A", "A1", "B", "C")), { problem, _ -> problems.add(problem) }, true)
        assertEquals("Die Spalte 2 ('A1') ist unerwartet.", problems[0])
        assertEquals(1, problems.size)
    }

    @Test
    fun `detectHeaderProblems detects two superfluous columns`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(
                Values.of(listOf("A", "A1", "A2", "B", "C")),
                { problem, _ -> problems.add(problem) },
                true
        )
        assertEquals("Die Spalte 2 ('A1') ist unerwartet.", problems[0])
        assertEquals("Die Spalte 3 ('A2') ist unerwartet.", problems[1])
        assertEquals(2, problems.size)
    }

    @Test
    fun `detectHeaderProblems detects wrong column`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C", "D"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(Values.of(listOf("A", "F", "G", "H")), { problem, _ -> problems.add(problem) }, true)
        assertEquals("In der Spalte 2 wurde 'B' erwartet, aber 'F' gefunden.", problems[0])
        assertEquals("In der Spalte 3 wurde 'C' erwartet, aber 'G' gefunden.", problems[1])
        assertEquals("In der Spalte 4 wurde 'D' erwartet, aber 'H' gefunden.", problems[2])
        assertEquals(3, problems.size)
    }

    @Test
    fun `detectHeaderProblems warns for aliased columns`() {
        val dict = ImportDictionary()
        dict.addField(FieldDefinition.stringField("A", 25).addAlias("XA"))
        dict.useMapping(listOf("A", "B", "C", "D"))
        val problems = mutableListOf<Tuple<String, Boolean>>()
        val problemDetected = dict.detectHeaderProblems(Values.of(listOf("XA", "B", "C", "D")), { problem, errorFlag ->
            problems.add(Tuple.create(problem, errorFlag))
        }, true)
        assertFalse(problemDetected)
        assertEquals("In der Spalte 1 wurde 'A' erwartet, aber 'XA' gefunden.", problems[0].first)
        assertEquals(false, problems[0].second)
        assertEquals(1, problems.size)
    }

    @Test
    fun `detectHeaderProblems detects additional columns`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(Values.of(listOf("A", "B", "C", "D")), { problem, _ -> problems.add(problem) }, true)
        assertEquals("Die Spalte 4 ('D') ist unerwartet.", problems[0])
        assertEquals(1, problems.size)
    }

    @Test
    fun `detectHeaderProblems detects missing columns`() {
        val dict = ImportDictionary()
        dict.useMapping(listOf("A", "B", "C", "D"))
        val problems = mutableListOf<String>()
        dict.detectHeaderProblems(Values.of(listOf("A", "B", "C")), { problem, _ -> problems.add(problem) }, true)
        assertEquals("Die Spalte 4 ('D') fehlt.", problems[0])
        assertEquals(1, problems.size)
    }
}
