/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.db.mixing.types.StringList;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a string list value backed by a {@link LookupTable} to be used in database entities.
 * Note that for single strings, {@link LookupValue} can be used. Also note that internally the values are stored
 * as {@link StringList}.
 */
public class LookupValues extends StringList {
    private final String lookupTableName;
    private LookupTable table;

    private final LookupValue.Display display;
    private final LookupValue.Display extendedDisplay;
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
     * @deprecated use the new constructor with all fields instead
     */
    @Deprecated
    public LookupValues(String lookupTableName,
                        LookupValue.CustomValues customValues,
                        LookupValue.Display display,
                        LookupValue.Export export) {
        this.lookupTableName = lookupTableName;
        this.customValues = customValues;
        this.display = display;
        this.extendedDisplay = display;
        this.export = export;
    }

    /**
     * Creates a new list with the given settings.
     * <p>
     * Note that when using the list in database entities, the field has to be final, as the actual values
     * is stored internally.
     *
     * @param lookupTableName the lookup table used to draw metadata from
     * @param customValues    determines if custom values are supported
     * @param display         determines how values are rendered in the UI
     * @param extendedDisplay determines how values are rendered in the UI in cases where we want to be more verbose
     * @param export          determines how values are rendered in exports
     */
    public LookupValues(String lookupTableName,
                        LookupValue.CustomValues customValues,
                        LookupValue.Display display,
                        LookupValue.Display extendedDisplay,
                        LookupValue.Export export) {
        this.lookupTableName = lookupTableName;
        this.customValues = customValues;
        this.display = display;
        this.extendedDisplay = extendedDisplay;
        this.export = export;
    }

    /**
     * Creates a new list using default settings.
     * <p>
     * Note that when using the list in database entities, the field has to be final, as the actual values
     * are stored internally.
     * <p>
     * By default, <tt>LookupValues</tt> doesn't support custom values, shows the name in the UI and exports the
     * normalized code.
     *
     * @param lookupTableName the lookup table used to draw metadata from
     */
    public LookupValues(String lookupTableName) {
        this(lookupTableName,
             LookupValue.CustomValues.REJECT,
             LookupValue.Display.NAME,
             LookupValue.Display.NAME,
             LookupValue.Export.CODE);
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

    /**
     * Resolves a string to present to the user for each value according to {@link #display}.
     *
     * @return a list of tuples containing the value codes, along with their display strings
     * @see LookupValue.Display#resolveDisplayString()
     */
    public List<Tuple<String, String>> resolveDisplayStrings() {
        return data().stream()
                     .map(code -> Tuple.create(code, display.resolveDisplayString(getTable(), code)))
                     .collect(Collectors.toList());
    }

    /**
     * Provides access to {@link #customValues} as a simple boolean.
     *
     * @return true if the field {@link sirius.biz.codelists.LookupValue.CustomValues#ACCEPT accepts} custom values, false otherwise
     */
    public boolean acceptsCustomValues() {
        return customValues == LookupValue.CustomValues.ACCEPT;
    }

    public LookupValue.Display getDisplay() {
        return display;
    }

    public LookupValue.Display getExtendedDisplay() {
        return extendedDisplay;
    }

    public LookupValue.Export getExport() {
        return export;
    }

    public LookupValue.CustomValues getCustomValues() {
        return customValues;
    }

    public String getTableName() {
        return lookupTableName;
    }
}
