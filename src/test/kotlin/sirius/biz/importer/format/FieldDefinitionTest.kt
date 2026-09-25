/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.nls.NLS
import kotlin.test.assertEquals

/**
 * Tests the [FieldDefinition] class.
 */
@ExtendWith(SiriusExtension::class)
class FieldDefinitionTest {

    @Test
    fun `added remarks are listed after the remarks of the checks`() {
        val field = FieldDefinition.stringField("test", 10).addRemark("Separated by spaces")

        assertEquals(listOf(LengthCheck(10).generateRemark(), "Separated by spaces"), field.getRemarks())
    }

    @Test
    fun `added remarks are auto translated`() {
        val field = FieldDefinition.stringField("test").addRemark("\$FieldDefinition.remarks")

        assertEquals(listOf(NLS.get("FieldDefinition.remarks")), field.getRemarks())
    }

    @Test
    fun `empty remarks are ignored`() {
        val field = FieldDefinition.stringField("test").addRemark(null).addRemark("")

        assertEquals(emptyList(), field.getRemarks())
    }
}
