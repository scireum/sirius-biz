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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [EntityParameter] class.
 */
@ExtendWith(SiriusExtension::class)
class EntityParameterTest {

    @Test
    fun `a valid entity id is accepted`() {
        val entity = createEntity("First")
        val parameter = ParameterTestEntityParameter("test", "Test").build()

        assertEquals(entity.idAsString, parameter.checkAndTransform(Value.of(entity.idAsString)))
    }

    @Test
    fun `an unknown entity id is rejected`() {
        val deletedId = createDeletedEntityId()
        val parameter = ParameterTestEntityParameter("test", "Test").build()

        assertThrows<HandledException> {
            parameter.checkAndTransform(Value.of(deletedId))
        }
    }

    @Test
    fun `empty input yields null`() {
        val parameter = ParameterTestEntityParameter("test", "Test").build()

        assertNull(parameter.checkAndTransform(Value.EMPTY))
        assertNull(parameter.checkAndTransform(Value.of("")))
    }

    @Test
    fun `a stored id resolves into the entity`() {
        val entity = createEntity("First")
        val parameter = ParameterTestEntityParameter("test", "Test").build()

        assertEquals(entity.idAsString, parameter.get(mapOf("test" to entity.idAsString)).orElseThrow().idAsString)
    }

    @Test
    fun `an unresolvable stored id resolves empty`() {
        val deletedId = createDeletedEntityId()
        val parameter = ParameterTestEntityParameter("test", "Test").build()

        assertTrue(parameter.get(mapOf("test" to deletedId)).isEmpty)
    }

    @Test
    fun `renderCurrentValue provides id and label of the selected entity`() {
        val entity = createEntity("First")
        val builder = ParameterTestEntityParameter("test", "Test")

        val idAndLabel = builder.renderCurrentValue(mapOf("test" to entity.idAsString))

        assertEquals(entity.idAsString, idAndLabel.first)
        assertEquals("First", idAndLabel.second)
    }

    @Test
    fun `the custom autocomplete uri is used as autocomplete url`() {
        assertEquals(
            "/test/autocomplete",
            ParameterTestEntityParameter("test", "Test", "/test/autocomplete").autocompleteUrl
        )
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
 * Provides a concrete [EntityParameter] for [ParameterTestEntity] to test the base class with.
 */
private class ParameterTestEntityParameter(
    name: String,
    label: String,
    private val autocompleteUri: String? = null
) :
    EntityParameter<ParameterTestEntity, ParameterTestEntityParameter>(name, label) {

    override fun getType(): Class<ParameterTestEntity> {
        return ParameterTestEntity::class.java
    }

    override fun getCustomAutocompleteUri(): String? {
        return autocompleteUri
    }
}
