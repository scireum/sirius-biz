/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.pagehelper

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.web.MongoPageHelper
import sirius.biz.web.pagehelper.MongoPageHelperEntity
import sirius.db.mongo.Mango
import sirius.kernel.SiriusExtension
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part
import sirius.web.http.WebContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the [MongoPageHelper] class.
 */
@ExtendWith(SiriusExtension::class)
class MongoPageHelperTest {

    @Test
    fun `test boolean aggregation without value selected`() {
        val pageHelper = MongoPageHelper.withQuery(
                mango.select(MongoPageHelperEntity::class.java)
        )
        val webContext = CallContext.getCurrent().get(WebContext::class.java)
        webContext.javaClass.getDeclaredField("queryString").apply {
            isAccessible = true
            set(webContext, mapOf<String, List<String>>())
        }
        pageHelper.withContext(webContext)
        pageHelper.addBooleanAggregation(MongoPageHelperEntity.BOOLEAN_FIELD)
        val page = pageHelper.asPage()
        assertTrue { page.hasFacets() }
        assertEquals(1, page.facets.size)
        val facet = page.facets[0]
        assertEquals("Bool Feld", facet.title)
        assertEquals("booleanField", facet.name)
        val facetItems = facet.allItems
        assertEquals(2, facetItems.size)
        assertEquals("true", facetItems[0].key)
        assertEquals(3, facetItems[0].count)
        assertEquals(false, facetItems[0].isActive)
        assertEquals("false", facetItems[1].key)
        assertEquals(2, facetItems[1].count)
        assertEquals(false, facetItems[1].isActive)
    }

    @Test
    fun `test boolean aggregation with value selected`() {
        val pageHelper = MongoPageHelper.withQuery(
                mango.select(
                        MongoPageHelperEntity::class.java
                )
        )
        val webContext = CallContext.getCurrent().get(WebContext::class.java)
        webContext.javaClass.getDeclaredField("queryString").apply {
            isAccessible = true
            set(webContext, mapOf("booleanField" to listOf("true")))
        }
        pageHelper.withContext(webContext)
        pageHelper.addBooleanAggregation(MongoPageHelperEntity.BOOLEAN_FIELD)
        val page = pageHelper.asPage()
        assertTrue { page.hasFacets() }
        assertEquals(1, page.facets.size)
        val facet = page.facets[0]
        assertEquals("Bool Feld", facet.title)
        assertEquals("booleanField", facet.name)
        val facetItems = facet.allItems
        assertEquals(1, facetItems.size)
        assertEquals("true", facetItems[0].key)
        assertEquals(3, facetItems[0].count)
        assertEquals(true, facetItems[0].isActive)
    }

    @Test
    fun `test term aggregation without value selected`() {
        val pageHelper = MongoPageHelper.withQuery(
                mango.select(MongoPageHelperEntity::class.java)
        )
        val webContext = CallContext.getCurrent().get(WebContext::class.java)
        webContext.javaClass.getDeclaredField("queryString").apply {
            isAccessible = true
            set(webContext, mapOf<String, List<String>>())
        }
        pageHelper.withContext(webContext)
        pageHelper.addTermAggregation(MongoPageHelperEntity.STRING_FIELD)
        val page = pageHelper.asPage()
        assertTrue { page.hasFacets() }
        assertEquals(1, page.facets.size)
        val facet = page.facets[0]
        assertEquals("String Feld", facet.title)
        assertEquals("stringField", facet.name)
        val facetItems = facet.allItems
        assertEquals(2, facetItems.size)
        assertEquals("field-value-a", facetItems[0].key)
        assertEquals(3, facetItems[0].count)
        assertEquals(false, facetItems[0].isActive)
        assertEquals("field-value-b", facetItems[1].key)
        assertEquals(2, facetItems[1].count)
        assertEquals(false, facetItems[1].isActive)
    }

    @Test
    fun `test term aggregation with value selected`() {
        val pageHelper = MongoPageHelper.withQuery(
                mango.select(MongoPageHelperEntity::class.java)
        )
        val webContext = CallContext.getCurrent().get(WebContext::class.java)
        webContext.javaClass.getDeclaredField("queryString").apply {
            isAccessible = true
            set(webContext, mapOf("stringField" to listOf("field-value-a")))
        }
        pageHelper.withContext(webContext)
        pageHelper.addTermAggregation(MongoPageHelperEntity.STRING_FIELD)
        val page = pageHelper.asPage()
        assertTrue { page.hasFacets() }
        assertEquals(1, page.facets.size)
        val facet = page.facets[0]
        assertEquals("String Feld", facet.title)
        assertEquals("stringField", facet.name)
        val facetItems = facet.allItems
        assertEquals(1, facetItems.size)
        assertEquals("field-value-a", facetItems[0].key)
        assertEquals(3, facetItems[0].count)
        assertEquals(true, facetItems[0].isActive)
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango

        @BeforeAll
        @JvmStatic
        fun setup() {
            MongoPageHelperEntity().apply {
                isBooleanField = true
                stringField = "field-value-a"
                mango.update(this)
            }

            MongoPageHelperEntity().apply {
                isBooleanField = true
                stringField = "field-value-b"
                mango.update(this)
            }

            MongoPageHelperEntity().apply {
                isBooleanField = true
                stringField = "field-value-a"
                mango.update(this)
            }

            MongoPageHelperEntity().apply {
                isBooleanField = false
                stringField = "field-value-b"
                mango.update(this)
            }

            MongoPageHelperEntity().apply {
                isBooleanField = false
                stringField = "field-value-a"
                mango.update(this)
            }
        }
    }
}
