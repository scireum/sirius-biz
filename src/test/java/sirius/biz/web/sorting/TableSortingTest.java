/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.sorting;

import org.junit.jupiter.api.Test;
import sirius.biz.web.SortOrder;
import sirius.biz.web.TableSortOption;
import sirius.biz.web.TableSorting;
import sirius.biz.web.pagehelper.MongoPageHelperEntity;
import sirius.web.controller.Page;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TableSortingTest {

    @Test
    void fetchSortOptionsReturnsOptionsAttachedToPage() {
        TableSortOption option = new TableSortOption("String field", MongoPageHelperEntity.STRING_FIELD);
        Page<MongoPageHelperEntity> page =
                new Page<MongoPageHelperEntity>().withAttribute(TableSorting.ATTRIBUTE_SORT_OPTIONS, List.of(option));

        assertEquals(List.of(option), new TableSorting().fetchSortOptions(page));
    }

    @Test
    void fetchSortOptionsReturnsEmptyListForInvalidAttributeType() {
        Page<MongoPageHelperEntity> page =
                new Page<MongoPageHelperEntity>().withAttribute(TableSorting.ATTRIBUTE_SORT_OPTIONS, "invalid");

        assertEquals(List.of(), new TableSorting().fetchSortOptions(page));
    }

    @Test
    void sortOrderResolvesKnownParameterValues() {
        assertEquals(SortOrder.ASC, SortOrder.fromParameter(TableSorting.ORDER_ASC));
        assertEquals(SortOrder.DESC, SortOrder.fromParameter(TableSorting.ORDER_DESC));
    }

    @Test
    void sortOrderIgnoresInvalidParameterValue() {
        assertNull(SortOrder.fromParameter("invalid"));
    }
}
