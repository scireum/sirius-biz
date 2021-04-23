/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Represents a string value backed by a {@link LookupTable} to be used in database entities.
 * <p>
 * Being a string value, {@link sirius.db.mixing.annotations.Length} and {@link sirius.db.mixing.annotations.NullAllowed}
 * can be used as to further annotate a database field.
 */
public class LookupValue {

    private final String lookupTableName;
    private LookupTable table;

    private final Display display;
    private final Export export;
    private final CustomValues customValues;
    private String value;

    /**
     * Controls whether custom values (not contained in the underlying lookup table) are accepted or not.
     */
    public enum CustomValues {
        ACCEPT, REJECT
    }

    /**
     * Determines if the code itself or the (potentially translated) name are put into export files.
     */
    public enum Export {
        CODE, NAME
    }

    /**
     * Controls what is shown in the UI when rendering a value.
     */
    public enum Display {
        CODE, NAME, CODE_AND_NAME
    }

    @Part
    private static LookupTables lookupTables;

    /**
     * Creates a new value with the given settings.
     * <p>
     * Note that when using the value in database entities, the field has to be final, as the actual value
     * is stored internally.
     *
     * @param lookupTableName the lookup table used to draw metadata from
     * @param customValues    determines if custom values are supported
     * @param display         determines how values are rendered in the UI
     * @param export          determines how values are rendered in exports
     */
    public LookupValue(String lookupTableName, CustomValues customValues, Display display, Export export) {
        this.lookupTableName = lookupTableName;
        this.customValues = customValues;
        this.display = display;
        this.export = export;
    }

    /**
     * Creates a new value using default settings.
     * <p>
     * Note that when using the value in database entities, the field has to be final, as the actual value
     * is stored internally.
     * <p>
     * By default, a <tt>LookupValue</tt> doesn't support custom values, shows the name in the UI and exports the
     * normalized code.
     *
     * @param lookupTableName the lookup table used to draw metadata from
     */
    public LookupValue(String lookupTableName) {
        this(lookupTableName, CustomValues.REJECT, Display.NAME, Export.CODE);
    }

    /**
     * Determines if the currently stored value is a valid code in the underlying lookp table.
     *
     * @throws IllegalArgumentException if the currently stored code is invalid
     */
    public void verifyValue() {
        if (Strings.isFilled(value) && !getTable().normalize(value).isPresent()) {
            throw new IllegalArgumentException(NLS.fmtr("LookupValue.invalidValue").set("value", value).format());
        }
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
     * Resolves the name for the current value.
     *
     * @return the name of the current value in the current language, or an empty optional if no value is present or
     * if the code is unknown.
     */
    public Optional<String> fetchName() {
        return getTable().resolveName(value);
    }

    /**
     * Fetches the description for the current value.
     *
     * @return the description for the current value in the current language, or an empty optional if no value is
     * present or if the code is unknown.
     */
    public Optional<String> fetchDescription() {
        return getTable().resolveDescription(value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Display getDisplay() {
        return display;
    }

    public Export getExport() {
        return export;
    }

    public CustomValues getCustomValues() {
        return customValues;
    }
}
