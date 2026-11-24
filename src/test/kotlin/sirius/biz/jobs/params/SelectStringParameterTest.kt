/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import kotlin.test.assertEquals

/**
 * Tests the [SelectStringParameter] class.
 */
@ExtendWith(SiriusExtension::class)
class SelectStringParameterTest {

    @Test
    fun `formatValue outputs the label of the selected entry`() {
        val parameter = createParameter().build()

        assertEquals("Label B", parameter.formatValue("b"))
    }

    @Test
    fun `formatValue outputs an unknown key as it is`() {
        val parameter = createParameter().build()

        assertEquals("x", parameter.formatValue("x"))
    }

    @Test
    fun `formatValue uses the entries provider`() {
        val parameter = SelectStringParameter("test", "Test").withEntriesProvider { mapOf("a" to "Provided A") }
                                                             .build()

        assertEquals("Provided A", parameter.formatValue("a"))
    }

    private fun createParameter(): SelectStringParameter {
        return SelectStringParameter("test", "Test").withEntry("a", "Label A").withEntry("b", "Label B")
    }
}
