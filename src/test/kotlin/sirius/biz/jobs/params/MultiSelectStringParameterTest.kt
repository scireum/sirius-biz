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
import sirius.kernel.commons.Value
import tools.jackson.databind.node.ArrayNode
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [MultiSelectStringParameter] class (and thereby the encoding logic of [MultiSelectParameter]).
 */
@ExtendWith(SiriusExtension::class)
class MultiSelectStringParameterTest {

    @Test
    fun `a list of selected values is serialized as delimited string`() {
        val parameter = createParameter().build()

        assertEquals("a|b", parameter.checkAndTransform(Value.of(listOf("a", "b"))))
    }

    @Test
    fun `unknown values are silently dropped`() {
        val parameter = createParameter().build()

        assertEquals("a", parameter.checkAndTransform(Value.of(listOf("a", "x"))))
    }

    @Test
    fun `an empty or fully invalid selection yields null`() {
        val parameter = createParameter().build()

        assertNull(parameter.checkAndTransform(Value.EMPTY))
        assertNull(parameter.checkAndTransform(Value.of("")))
        assertNull(parameter.checkAndTransform(Value.of(listOf("x", "y"))))
    }

    @Test
    fun `a single plain value is accepted and trimmed`() {
        val parameter = createParameter().build()

        assertEquals("a", parameter.checkAndTransform(Value.of(" a ")))
    }

    @Test
    fun `an already encoded string is split and validated per token`() {
        val parameter = createParameter().build()

        assertEquals("a|b", parameter.checkAndTransform(Value.of("a|b")))
        assertEquals("a", parameter.checkAndTransform(Value.of("a|x")))
    }

    @Test
    fun `list values containing the delimiter cannot be selected`() {
        val parameter = createParameter().build()

        assertNull(parameter.checkAndTransform(Value.of(listOf("a|b"))))
    }

    @Test
    fun `the default provider kicks in for missing input`() {
        val parameter = createParameter().withDefaultProvider { listOf("a", "b") }.build()

        assertEquals("a|b", parameter.checkAndTransform(Value.EMPTY))
    }

    @Test
    fun `stored values are resolved as list`() {
        val parameter = createParameter().build()

        assertEquals(listOf("a", "b"), parameter.get(mapOf("test" to "a|b")).orElseThrow())
    }

    @Test
    fun `stale stored values are skipped on resolve`() {
        val parameter = createParameter().build()

        assertEquals(listOf("a"), parameter.get(mapOf("test" to "a|x")).orElseThrow())
    }

    @Test
    fun `a stored value without any resolvable token resolves empty`() {
        val parameter = createParameter().build()

        assertTrue(parameter.get(mapOf("test" to "x")).isEmpty)
        assertTrue(parameter.get(mapOf()).isEmpty)
    }

    @Test
    fun `getValues enumerates all options with their selection state`() {
        val builder = createParameter()

        val values = builder.getValues(mapOf("test" to "a|c"))

        assertEquals(listOf("a", "b", "c"), values.map { it.name() })
        assertEquals(listOf("Label A", "Label B", "Label C"), values.map { it.label() })
        assertTrue(values[0].selected())
        assertFalse(values[1].selected())
        assertTrue(values[2].selected())
    }

    @Test
    fun `computeValueUpdate produces value and text pairs`() {
        val parameter = createParameter().withUpdater { _ -> Optional.of(listOf("a", "b")) }.build()

        val update = parameter.updateValue(mapOf()).orElseThrow() as ArrayNode

        assertEquals(2, update.size())
        assertEquals("a", update.get(0).get("value").asString(""))
        assertEquals("Label A", update.get(0).get("text").asString(""))
        assertEquals("b", update.get(1).get("value").asString(""))
        assertEquals("Label B", update.get(1).get("text").asString(""))
    }

    private fun createParameter(): MultiSelectStringParameter {
        return MultiSelectStringParameter("test", "Test").withEntry("a", "Label A")
                                                                        .withEntry("b", "Label B")
                                                                        .withEntry("c", "Label C")
    }
}
