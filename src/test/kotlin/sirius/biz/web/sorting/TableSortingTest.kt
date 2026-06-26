/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.sorting

import org.junit.jupiter.api.Test
import sirius.biz.web.SortOrder
import sirius.biz.web.TableSortOption
import sirius.biz.web.TableSorting
import sirius.biz.web.pagehelper.MongoPageHelperEntity
import sirius.web.controller.Page
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests the [TableSorting] helper.
 */
class TableSortingTest {

    @Test
    fun `fetchSortOptions returns options attached to page`() {
        val option = TableSortOption("String field", MongoPageHelperEntity.STRING_FIELD)
        val page = Page<MongoPageHelperEntity>().withAttribute(TableSorting.ATTRIBUTE_SORT_OPTIONS, listOf(option))

        assertEquals(listOf(option), TableSorting().fetchSortOptions(page))
    }

    @Test
    fun `fetchSortOptions returns empty list for invalid attribute type`() {
        val page = Page<MongoPageHelperEntity>().withAttribute(TableSorting.ATTRIBUTE_SORT_OPTIONS, "invalid")

        assertEquals(emptyList(), TableSorting().fetchSortOptions(page))
    }

    @Test
    fun `sortOrder resolves known parameter values`() {
        assertEquals(SortOrder.ASC, SortOrder.fromParameter(TableSorting.ORDER_ASC))
        assertEquals(SortOrder.DESC, SortOrder.fromParameter(TableSorting.ORDER_DESC))
    }

    @Test
    fun `sortOrder ignores invalid parameter value`() {
        assertNull(SortOrder.fromParameter("invalid"))
    }
}
