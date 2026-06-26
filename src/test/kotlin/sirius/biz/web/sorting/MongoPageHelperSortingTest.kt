/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.sorting

import org.junit.jupiter.api.Test
import sirius.biz.web.MongoPageHelper
import sirius.biz.web.TableSortOption
import sirius.biz.web.pagehelper.MongoPageHelperEntity
import sirius.db.mongo.MongoQuery
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the table sorting support in [MongoPageHelper].
 */
class MongoPageHelperSortingTest {

    @Test
    fun `addSortableOption registers option and mapping name as sort key`() {
        val helper = TestMongoPageHelper()
        val option = TableSortOption("String field", MongoPageHelperEntity.STRING_FIELD)

        assertSame(helper, helper.addSortableOption(option))

        assertEquals(listOf(option), helper.getSortableOptions())
        assertTrue { helper.getSortableKeys().contains(MongoPageHelperEntity.STRING_FIELD.name) }
    }

    @Test
    fun `addSortableOptions registers all options`() {
        val helper = TestMongoPageHelper()
        val stringOption = TableSortOption("String field", MongoPageHelperEntity.STRING_FIELD)
        val booleanOption = TableSortOption("Boolean field", MongoPageHelperEntity.BOOLEAN_FIELD)

        assertSame(helper, helper.addSortableOptions(listOf(stringOption, booleanOption)))

        assertEquals(listOf(stringOption, booleanOption), helper.getSortableOptions())
        assertTrue { helper.getSortableKeys().contains(MongoPageHelperEntity.STRING_FIELD.name) }
        assertTrue { helper.getSortableKeys().contains(MongoPageHelperEntity.BOOLEAN_FIELD.name) }
    }

    @Test
    fun `rejects null sortable inputs`() {
        val helper = TestMongoPageHelper()

        assertFailsWith<NullPointerException> {
            helper.addSortableOption(null)
        }
        assertFailsWith<NullPointerException> {
            helper.addSortableOptions(null)
        }
    }

    private class TestMongoPageHelper : MongoPageHelper<MongoPageHelperEntity>(null as MongoQuery<MongoPageHelperEntity>?) {

        fun getSortableOptions(): List<TableSortOption> = sortableOptions

        fun getSortableKeys(): List<String> = sortableFields.keys.toList()
    }
}
