/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Value
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import tools.jackson.databind.node.ArrayNode
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [MultiSelectEntityParameter] class.
 */
@ExtendWith(SiriusExtension::class)
class MultiSelectEntityParameterTest {

    @Test
    fun `selected entities are serialized as delimited id string`() {
        val first = createEntity("First")
        val second = createEntity("Second")
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").build()

        val serialized = parameter.checkAndTransform(Value.of(listOf(first.idAsString, second.idAsString)))

        assertEquals("${first.idAsString}|${second.idAsString}", serialized)
    }

    @Test
    fun `unknown entity ids are rejected`() {
        val first = createEntity("First")
        val unknownId = createDeletedEntityId()
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").build()

        assertThrows<HandledException> {
            parameter.checkAndTransform(Value.of(listOf(first.idAsString, unknownId)))
        }
    }

    @Test
    fun `empty input yields null`() {
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").build()

        assertNull(parameter.checkAndTransform(Value.EMPTY))
        assertNull(parameter.checkAndTransform(Value.of("")))
        assertNull(parameter.checkAndTransform(Value.of(listOf<String>())))
    }

    @Test
    fun `stored ids resolve into entities in selection order`() {
        val first = createEntity("First")
        val second = createEntity("Second")
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").build()

        val resolved = parameter.get(mapOf("test" to "${second.idAsString}|${first.idAsString}")).orElseThrow()

        assertEquals(listOf(second.idAsString, first.idAsString), resolved.map { it.idAsString })
    }

    @Test
    fun `unresolvable ids are skipped on resolve`() {
        val first = createEntity("First")
        val deletedId = createDeletedEntityId()
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").build()

        val resolved = parameter.get(mapOf("test" to "${first.idAsString}|$deletedId")).orElseThrow()

        assertEquals(listOf(first.idAsString), resolved.map { it.idAsString })
    }

    @Test
    fun `a selection of only unresolvable ids resolves empty`() {
        val deletedId = createDeletedEntityId()
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").build()

        assertTrue(parameter.get(mapOf("test" to deletedId)).isEmpty)
    }

    @Test
    fun `getValues renders only the selected entities`() {
        val first = createEntity("First")
        val second = createEntity("Second")
        val builder = ParameterTestEntityMultiSelectParameter("test", "Test")

        val values = builder.getValues(mapOf("test" to "${first.idAsString}|${second.idAsString}"))

        assertEquals(listOf(first.idAsString, second.idAsString), values.map { it.name() })
        assertEquals(listOf("First", "Second"), values.map { it.label() })
        assertTrue(values.all { it.selected() })
    }

    @Test
    fun `getSuggestionUri uses the custom autocomplete uri or falls back to an empty string`() {
        assertEquals(
            "/test/autocomplete",
            ParameterTestEntityMultiSelectParameter("test", "Test", "/test/autocomplete").suggestionUri
        )
        assertEquals("", ParameterTestEntityMultiSelectParameter("test", "Test").suggestionUri)
    }

    @Test
    fun `computeValueUpdate produces id and label pairs`() {
        val first = createEntity("First")
        val parameter = ParameterTestEntityMultiSelectParameter("test", "Test").withUpdater { _ ->
            Optional.of(listOf(first))
        }.build()

        val update = parameter.updateValue(mapOf()).orElseThrow() as ArrayNode

        assertEquals(1, update.size())
        assertEquals(first.idAsString, update.get(0).get("value").asString(""))
        assertEquals("First", update.get(0).get("text").asString(""))
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA
    }

    private fun createEntity(name: String): ParameterTestEntity {
        val entity = ParameterTestEntity()
        entity.name = name
        oma.update(entity)
        return entity
    }

    private fun createDeletedEntityId(): String {
        val entity = createEntity("Deleted")
        val id = entity.idAsString
        oma.delete(entity)
        return id
    }
}

/**
 * Provides a concrete [MultiSelectEntityParameter] for [ParameterTestEntity] to test the base class with.
 */
private class ParameterTestEntityMultiSelectParameter(
    name: String,
    label: String,
    private val autocompleteUri: String? = null
) :
    MultiSelectEntityParameter<ParameterTestEntity, ParameterTestEntityMultiSelectParameter>(name, label) {

    override fun getType(): Class<ParameterTestEntity> {
        return ParameterTestEntity::class.java
    }

    override fun getCustomAutocompleteUri(): String? {
        return autocompleteUri
    }
}
