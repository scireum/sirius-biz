/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.jupiter.Jupiter;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all known {@link LookupTable lookup tables}.
 * <p>
 * This is mainly required to instantiate the concrete type of lookup table for a given table name. Depending on
 * the configuration, this could either be a {@link CodeLists code list} or a table backed by
 * {@link sirius.biz.jupiter.InfoGraphDB}.
 *
 * @see LookupTable
 */
@Register(classes = LookupTables.class)
public class LookupTables {

    private static final String CONFIG_BLOCK_LOOKUP_TABLES = "lookup-tables";
    private static final String CONFIG_KEY_TABLE = "table";
    private static final String CONFIG_KEY_CODE_LIST = "codeList";
    private static final String CONFIG_KEY_CUSTOM_TABLE = "customTable";
    private static final String CONFIG_KEY_FILTER_SET = "filterSet";

    @Part
    @Nullable
    private Jupiter jupiter;

    private final Map<String, LookupTable> tables = new ConcurrentHashMap<>();

    /**
     * Fetches the lookup table with the given name.
     *
     * @param lookupTableName the name of the lookup table
     * @return the table for the given name. If no matching configuration is found, we simply redirect to the
     * code list with the same name
     */
    public LookupTable fetchTable(String lookupTableName) {
        if (!tables.containsKey(lookupTableName)) {
            LookupTable table = makeTable(lookupTableName);
            tables.put(lookupTableName, table);
        }
        return tables.get(lookupTableName);
    }

    private LookupTable makeTable(String name) {
        Extension extension = Sirius.getSettings().getExtension(CONFIG_BLOCK_LOOKUP_TABLES, name);
        String baseTable = extension.get(CONFIG_KEY_TABLE).asString();

        // If no IDB config is present, ...
        if (Strings.isEmpty(baseTable)) {
            String codeList = extension.get(CONFIG_KEY_CODE_LIST).asString();
            if (Sirius.isTest()) {
                String json = extension.get(TestJsonLookupTable.CONFIG_KEY_TEST_DATA_JSON).asString();
                if (Strings.isEmpty(codeList) && Strings.isEmpty(json)) {
                    // ...and we are running in test mode and no codeList is given
                    return new TestExtensionLookupTable(extension);
                }
                if (Strings.isFilled(json)) {
                    // ...but a json table is given
                    return new TestJsonLookupTable(extension);
                }
            }
            // ...and Jupiter is disabled, we resort to code list based tables
            if (jupiter == null) {
                return new CodeListLookupTable(extension, Strings.firstFilled(codeList, name));
            }
        }

        // Note: to ensure the configured tables are loaded in the correct order, the base table needs to be resolved
        //       individually in each of the following steps.
        // 1. If a custom table is given, combine it with the base table
        Optional<LookupTable> customLookupTable =
                loadAsCustomTable(extension, extension.get(CONFIG_KEY_CUSTOM_TABLE).asString(), baseTable);
        // 2. Apply the filter set, if given
        Optional<LookupTable> filteredLookupTable = loadAsFilteredTable(extension,
                                                                        extension.get(CONFIG_KEY_FILTER_SET).asString(),
                                                                        customLookupTable,
                                                                        baseTable);
        // 3. return the (filtered) (custom) lookup table
        return filteredLookupTable.or(() -> customLookupTable)
                                  .orElseGet(() -> new IDBLookupTable(extension,
                                                                      jupiter.getDefault().idb().table(baseTable)));
    }

    private Optional<LookupTable> loadAsCustomTable(Extension extension, String customTable, String baseTable) {
        if (Strings.isFilled(customTable)) {
            return Optional.of(new CustomLookupTable(extension, fetchTable(customTable), fetchTable(baseTable)));
        }
        return Optional.empty();
    }

    private Optional<LookupTable> loadAsFilteredTable(Extension extension,
                                                      String filterSet,
                                                      Optional<LookupTable> customTable,
                                                      String baseTable) {
        if (Strings.isFilled(filterSet)) {
            return Optional.of(new IDBFilteredLookupTable(extension,
                                                          customTable.orElse(fetchTable(baseTable)),
                                                          jupiter.getDefault().idb().set(filterSet)));
        }
        return Optional.empty();
    }
}
