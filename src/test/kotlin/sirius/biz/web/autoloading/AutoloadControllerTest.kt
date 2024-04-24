/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.autoloading

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.mongo.Mango
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.web.http.TestRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests the auto-loading mechanism via [AutoloadController] by using the [AutoLoadEntity] as a test entity.
 */
@ExtendWith(SiriusExtension::class)
class AutoloadControllerTests {

    @Test
    fun `Creation with autoload works`() {
        val response = TestRequest.SAFEPOST("/auto-load-controller/new")
                .withParameter("stringField", "string1")
                .withParameter("intField", 42)
                .withParameter("listField", listOf("listItem1", "listItem2"))
                .execute()
        val id = response.contentAsJson.path("id").asText(null)
        assertNotNull(id)
        mango.find(AutoLoadEntity::class.java, id).get().apply {
            assertEquals("string1", stringField)
            assertEquals(42, intField)
            assertEquals(2, listField.size())
            assertEquals("listItem1", listField.data()[0])
            assertEquals("listItem2", listField.data()[1])
        }
    }

    @Test
    fun `Update with autoload works`() {
        val entity = AutoLoadEntity()
        entity.stringField = "string-not-autoloaded"
        entity.intField = 1337
        entity.listField.modify().add("starterListItem")
        mango.update(entity)
        val response = TestRequest.SAFEPOST("/auto-load-controller/" + entity.getId())
                .withParameter("stringField", "string-autoloaded")
                .withParameter("intField", 1)
                .withParameter("listField", listOf("listItem3", "listItem4"))
                .execute()
        val id = response.contentAsJson.path("id").asText(null)
        assertNotNull(id)
        mango.find(AutoLoadEntity::class.java, id).get().apply {
            assertEquals("string-autoloaded", stringField)
            assertEquals(1, intField)
            assertEquals(2, listField.size())
            assertEquals("listItem3", listField.data()[0])
            assertEquals("listItem4", listField.data()[1])
        }
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango
    }
}
