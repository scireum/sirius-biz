/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.sorting;

import org.junit.jupiter.api.Test;
import sirius.biz.web.MongoPageHelper;
import sirius.biz.web.TableSortOption;
import sirius.biz.web.pagehelper.MongoPageHelperEntity;
import sirius.db.mongo.MongoQuery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoPageHelperSortingTest {

    @Test
    void addSortableOptionRegistersOptionAndMappingNameAsSortKey() {
        TestMongoPageHelper helper = new TestMongoPageHelper();
        TableSortOption option = new TableSortOption("String field", MongoPageHelperEntity.STRING_FIELD);

        assertSame(helper, helper.addSortableOption(option));

        assertEquals(List.of(option), helper.getSortableOptions());
        assertTrue(helper.getSortableKeys().contains(MongoPageHelperEntity.STRING_FIELD.getName()));
    }

    @Test
    void addSortableOptionsRegistersAllOptions() {
        TestMongoPageHelper helper = new TestMongoPageHelper();
        TableSortOption stringOption = new TableSortOption("String field", MongoPageHelperEntity.STRING_FIELD);
        TableSortOption booleanOption = new TableSortOption("Boolean field", MongoPageHelperEntity.BOOLEAN_FIELD);

        assertSame(helper, helper.addSortableOptions(List.of(stringOption, booleanOption)));

        assertEquals(List.of(stringOption, booleanOption), helper.getSortableOptions());
        assertTrue(helper.getSortableKeys().contains(MongoPageHelperEntity.STRING_FIELD.getName()));
        assertTrue(helper.getSortableKeys().contains(MongoPageHelperEntity.BOOLEAN_FIELD.getName()));
    }

    @Test
    void rejectsNullSortableInputs() {
        TestMongoPageHelper helper = new TestMongoPageHelper();

        assertAll("null sortable inputs",
                  () -> assertThrows(NullPointerException.class, () -> helper.addSortableOption(null)),
                  () -> assertThrows(NullPointerException.class, () -> helper.addSortableOptions(null)));
    }

    private static class TestMongoPageHelper extends MongoPageHelper<MongoPageHelperEntity> {

        private TestMongoPageHelper() {
            super((MongoQuery<MongoPageHelperEntity>) null);
        }

        private List<TableSortOption> getSortableOptions() {
            return sortableOptions;
        }

        private List<String> getSortableKeys() {
            return sortableFields.keySet().stream().toList();
        }
    }
}
