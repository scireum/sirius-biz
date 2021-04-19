/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.db.mixing.types.StringList;
import sirius.kernel.di.std.Part;

/**
 * Represents a string list value backed by a {@link LookupTable} to be used in database entities.
 * Note that for single strings, {@link LookupValue} can be used. Also note that internally the values are stored
 * as {@link StringList}.
 */
public class LookupValues extends StringList {
    private final String lookupTableName;
    private LookupTable table;

    private final LookupValue.Display display;
    private final LookupValue.Export export;
    private final LookupValue.CustomValues customValues;

    @Part
    private static LookupTables lookupTables;

    /**
     * Creates a new list with the given settings.
     * <p>
     * Note that when using the list in database entities, the field has to be final, as the actual values
     * is stored internally.
     *
     * @param lookupTableName the lookup table used to draw metadata from
     * @param customValues    determines if custom values are supported
     * @param display         determines how values are rendered in the UI
     * @param export          determines how values are rendered in exports
     */
    public LookupValues(String lookupTableName,
                        LookupValue.CustomValues customValues,
                        LookupValue.Display display,
                        LookupValue.Export export) {
        this.lookupTableName = lookupTableName;
        this.customValues = customValues;
        this.display = display;
        this.export = export;
    }

    /**
     * Creates a new list using default settings.
     * <p>
     * Note that when using the list in database entities, the field has to be final, as the actual values
     * is stored internally.
     * <p>
     * By default, <tt>LookupValues</tt> doesn't support custom values, shows the name in the UI and exports the
     * normalized code.
     *
     * @param lookupTableName the lookup table used to draw metadata from
     */
    public LookupValues(String lookupTableName) {
        this(lookupTableName, LookupValue.CustomValues.REJECT, LookupValue.Display.NAME, LookupValue.Export.CODE);
    }

    /**
     * Provides access to the underlying {@link LookupTable}.
     *
     * @return the lookup table responsible for this field
     */
    public LookupTable getTable() {
        if (table == null) {
            table = lookupTables.fetchTable(lookupTableName);
        }

        return table;
    }

    public LookupValue.Display getDisplay() {
        return display;
    }

    public LookupValue.Export getExport() {
        return export;
    }

    public LookupValue.CustomValues getCustomValues() {
        return customValues;
    }
}
