/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
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
    private final Display extendedDisplay;
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
     * Determines if the code itself, or the (potentially translated) name are put into export files.
     */
    public enum Export {
        CODE, NAME
    }

    /**
     * Controls what is shown in the UI when rendering a value.
     */
    public enum Display {
        CODE, NAME, CODE_AND_NAME;

        /**
         * Resolves the correct display string for the given table and code using this display mode.
         *
         * @param table the table from where to fetch the name from
         * @param code  the code of the value
         * @return a display string for the given code in the given table
         */
        public String resolveDisplayString(LookupTable table, String code) {
            return switch (this) {
                case NAME -> table.resolveName(code).orElse(code);
                case CODE -> code;
                case CODE_AND_NAME -> table.resolveName(code)
                                           .map(name -> buildCodeAndNameString(name, code))
                                           .orElse(code);
            };
        }

        /**
         * Makes the correct display string for the given table entry using this display mode.
         *
         * @param entry the table entry to be displayed
         * @return a display string for the given table entry
         */
        public String makeDisplayString(LookupTableEntry entry) {
            return switch (this) {
                case NAME -> Value.of(entry.getName()).asString(entry.getCode());
                case CODE -> entry.getCode();
                case CODE_AND_NAME -> Value.of(entry.getName())
                                           .map(name -> buildCodeAndNameString(name.asString(), entry.getCode()))
                                           .orElse(entry.getCode());
            };
        }

        @Nonnull
        private String buildCodeAndNameString(String name, String code) {
            return name + " (" + code + ")";
        }
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
     * @param display         determines how values are rendered in the UI in most cases
     * @param extendedDisplay determines how values are rendered in the UI in cases where we want to be more verbose
     * @param export          determines how values are rendered in exports
     */
    public LookupValue(String lookupTableName,
                       CustomValues customValues,
                       Display display,
                       Display extendedDisplay,
                       Export export) {
        this.lookupTableName = lookupTableName;
        this.customValues = customValues;
        this.display = display;
        this.extendedDisplay = extendedDisplay;
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
        this(lookupTableName, CustomValues.REJECT, Display.NAME, Display.NAME, Export.CODE);
    }

    /**
     * Determines if the currently stored value is a valid code in the underlying lookup table.
     *
     * @throws IllegalArgumentException if the currently stored code is invalid
     */
    public void verifyValue() {
        if (Strings.isFilled(value) && getTable().normalize(value).isEmpty()) {
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
     * Resolves the name for the current value or returns the code itself if the name is unknown.
     *
     * @return the name of the current value or the code, if the name cannot be resolved. Note that this will return
     * an empty string if no value is present
     */
    public String forceFetchName() {
        if (Strings.isEmpty(value)) {
            return "";
        } else {
            return fetchName().orElse(value);
        }
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

    /**
     * Determines if a value is present.
     *
     * @return <tt>true</tt> if a non-null and non-empty value is present, <tt>false</tt> otherwise
     */
    public boolean isFilled() {
        return Strings.isFilled(value);
    }

    /**
     * Determines if no value is present.
     *
     * @return <tt>true</tt> if the value is null or empty, <tt>false otherwise</tt>
     */
    public boolean isEmpty() {
        return !isFilled();
    }

    /**
     * Resolves a string to present to the user for this value according to {@link #display}.
     *
     * @return a string to represent this value, its name or code or a combination
     * @see Display#resolveDisplayString()
     */
    public String resolveDisplayString() {
        return display.resolveDisplayString(getTable(), getValue());
    }

    /**
     * Resolves a string to present to the user for this value according to {@link #extendedDisplay}.
     * <p>
     * This is more verbose than {@link #resolveDisplayString()}.
     *
     * @return a string to represent this value, its name or code or a combination
     * @see Display#resolveDisplayString()
     */
    public String resolveExtendedDisplayString() {
        return extendedDisplay.resolveDisplayString(getTable(), getValue());
    }

    /**
     * Provides access to {@link #customValues} as a simple boolean.
     *
     * @return true if the field {@link CustomValues#ACCEPT accepts} custom values, false otherwise
     */
    public boolean acceptsCustomValues() {
        return customValues == CustomValues.ACCEPT;
    }

    @Override
    public String toString() {
        if (Strings.isEmpty(value)) {
            return lookupTableName + ": empty";
        } else {
            return Strings.apply("%s: %s (%s)", lookupTableName, value, fetchName().orElse("unknown"));
        }
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

    public Display getExtendedDisplay() {
        return extendedDisplay;
    }

    public Export getExport() {
        return export;
    }

    public CustomValues getCustomValues() {
        return customValues;
    }

    public String getTableName() {
        return lookupTableName;
    }
}
