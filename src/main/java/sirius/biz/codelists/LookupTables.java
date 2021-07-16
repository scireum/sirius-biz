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
     * Fetches the lookup with the given name.
     *
     * @param lookupTableName the name of the lookup table
     * @return the table for the given name. If no matching configuration is found, we simply redirect to the
     * code list with the same name
     */
    public LookupTable fetchTable(String lookupTableName) {
        return tables.computeIfAbsent(lookupTableName, this::makeTable);
    }

    private LookupTable makeTable(String name) {
        Extension extension = Sirius.getSettings().getExtension(CONFIG_BLOCK_LOOKUP_TABLES, name);
        String table = extension.get(CONFIG_KEY_TABLE).asString();

        // If no IDB config is present, or Jupiter is disabled, we resort to code list based tables...
        if (Strings.isEmpty(table) || jupiter == null) {
            return new CodeListLookupTable(extension, extension.get(CONFIG_KEY_CODE_LIST).asString(name));
        }

        // Directly access the given IDB table...
        LookupTable result = loadIDBLookupTable(extension, name, table);

        // ...if a custom table is also given, we use both tables together, there the custom table is always
        // "in front" of the normal table...
        String customTable = extension.get(CONFIG_KEY_CUSTOM_TABLE).asString();
        if (Strings.isFilled(customTable)) {
            LookupTable customLookupTable =
                    new IDBLookupTable(extension, jupiter.getDefault().idb().table(customTable));
            result = new CustomLookupTable(extension, customLookupTable, result);
        }

        // ...if a filter set is given, we apply this on the current intermediate result (which is either the table
        // or the table + custom table combined)...
        String filterSet = extension.get(CONFIG_KEY_FILTER_SET).asString();
        if (Strings.isFilled(filterSet)) {
            result = new IDBFilteredLookupTable(extension, result, jupiter.getDefault().idb().set(filterSet));
        }

        return result;
    }

    private LookupTable loadIDBLookupTable(Extension extension, String currentTable, String tableToLoad) {
        // Checks, if the table to load is a base table of the currently created table and loads it with the
        // correct configuration.
        if (!Strings.areEqual(currentTable, tableToLoad)) {
            Extension baseTableExtension = Sirius.getSettings().getExtension(CONFIG_BLOCK_LOOKUP_TABLES, tableToLoad);
            if (baseTableExtension != null) {
                return new IDBLookupTable(baseTableExtension, jupiter.getDefault().idb().table(tableToLoad));
            }
        }
        return new IDBLookupTable(extension, jupiter.getDefault().idb().table(tableToLoad));
    }
}
