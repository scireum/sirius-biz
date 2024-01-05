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
import sirius.biz.web.ElasticPageHelper
import sirius.biz.web.pagehelper.ElasticPageHelperEntity
import sirius.db.es.Elastic
import sirius.kernel.SiriusExtension
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part
import sirius.web.http.WebContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the [ElasticPageHelper] class.
 */
@ExtendWith(SiriusExtension::class)
class ElasticPageHelperTest {

    @Test
    fun `test boolean aggregation without value selected`() {
        val pageHelper = ElasticPageHelper.withQuery(
                elastic.select(ElasticPageHelperEntity::class.java)
        )
        val webContext = WebContext.getCurrent()
        webContext.javaClass.getDeclaredField("queryString").apply {
            isAccessible = true
            set(webContext, mapOf<String, List<String>>())
        }
        pageHelper.withContext(webContext)
        pageHelper.addBooleanAggregation(ElasticPageHelperEntity.BOOLEAN_FIELD)
        val page = pageHelper.asPage()
        assertTrue { page.hasFacets() }
        assertEquals(1, page.facets.size)
        val facet = page.facets[0]
        assertEquals("Bool Feld", facet.title)
        assertEquals("booleanField", facet.name)
        val facetItems = facet.allItems
        assertEquals(2, facetItems.size)
        assertEquals("1", facetItems[0].key)
        assertEquals(3, facetItems[0].count)
        assertEquals(false, facetItems[0].isActive)
        assertEquals("0", facetItems[1].key)
        assertEquals(2, facetItems[1].count)
        assertEquals(false, facetItems[1].isActive)
    }

    @Test
    fun `test boolean aggregation with value selected`() {
        val pageHelper = ElasticPageHelper.withQuery(
                elastic.select(ElasticPageHelperEntity::class.java)
        )
        val webContext = WebContext.getCurrent()
        webContext.javaClass.getDeclaredField("queryString").apply {
            isAccessible = true
            set(webContext, mapOf("booleanField" to listOf("1")))
        }
        pageHelper.withContext(webContext)
        pageHelper.addBooleanAggregation(ElasticPageHelperEntity.BOOLEAN_FIELD)
        val page = pageHelper.asPage()
        assertTrue { page.hasFacets() }
        assertEquals(1, page.facets.size)
        val facet = page.facets[0]
        assertEquals("Bool Feld", facet.title)
        assertEquals("booleanField", facet.name)
        val facetItems = facet.allItems
        assertEquals(1, facetItems.size)
        assertEquals("1", facetItems[0].key)
        assertEquals(3, facetItems[0].count)
        assertEquals(true, facetItems[0].isActive)
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var elastic: Elastic

        @BeforeAll
        @JvmStatic
        fun setup() {
            ElasticPageHelperEntity().apply {
                isBooleanField = true
                stringField = "field-value-a"
                elastic.update(this)
            }

            ElasticPageHelperEntity().apply {
                isBooleanField = true
                stringField = "field-value-b"
                elastic.update(this)
            }

            ElasticPageHelperEntity().apply {
                isBooleanField = true
                stringField = "field-value-a"
                elastic.update(this)
            }

            ElasticPageHelperEntity().apply {
                isBooleanField = false
                stringField = "field-value-b"
                elastic.update(this)
            }

            ElasticPageHelperEntity().apply {
                isBooleanField = false
                stringField = "field-value-a"
                elastic.update(this)
            }

            elastic.refresh(ElasticPageHelperEntity::class.java)
        }
    }
}
