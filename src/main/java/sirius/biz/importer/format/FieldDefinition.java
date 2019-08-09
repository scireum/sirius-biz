/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Lambdas;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.nls.NLS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Describes a field which is part of a {@link ImportDictionary}.
 * <p>
 * This can be used to import and check datasets (mostly row based ones like CSV or MS Excel).
 */
public class FieldDefinition {

    protected String name;
    protected String type;
    protected Supplier<String> label;
    protected Set<String> aliases = new HashSet<>();
    protected List<ValueCheck> checks = new ArrayList<>();

    /**
     * Creates a new field with the given name and type.
     *
     * @param name the name of the field
     * @param type the type description. Use helpers like {@link #typeString(Integer)} to generate one
     */
    public FieldDefinition(String name, String type) {
        this.name = name;
        this.type = type;
        addAlias(name);
    }

    /**
     * Helper to create a type description for a string field with a given length.
     *
     * @param maxLength the max length of the given field
     * @return a description to be shown to the user
     */
    public static String typeString(Integer maxLength) {
        if (maxLength == null || maxLength == 0) {
            return NLS.get("FieldDefinition.typeString.plain");
        }

        return NLS.fmtr("FieldDefinition.typeString.length").set("length", maxLength).format();
    }

    /**
     * Helper to create a type description for a numeric field with a given precision and scale.
     *
     * @param precision the precision of the field
     * @param scale     the scale of the field
     * @return a description to be shown to the user
     */
    public static String typeNumber(int precision, int scale) {
        if (precision == 0) {
            return NLS.get("FieldDefinition.typeNumber.plain");
        }

        return NLS.fmtr("FieldDefinition.typeNumber.length").set("precision", precision).set("scale", scale).format();
    }

    /**
     * Helper to create a type description for a boolean field.
     *
     * @return a description to be shown to the user
     */
    public static String typeBoolean() {
        return NLS.get("FieldDefinition.typeBoolean");
    }

    /**
     * Helper to create a type description for a date field.
     *
     * @return a description to be shown to the user
     */
    public static String typeDate() {
        return NLS.get("FieldDefinition.typeDate");
    }

    /**
     * Helper to create a type description for a field with an unknown type.
     *
     * @return a description to be shown to the user
     */
    public static String typeOther() {
        return NLS.get("FieldDefinition.typeOther");
    }

    /**
     * Specifies the label to use for this field.
     *
     * @param constantLabel the label to show
     * @return the field itself for fluent method calls
     */
    public FieldDefinition withLabel(String constantLabel) {
        this.label = () -> constantLabel;
        return this;
    }

    /**
     * Specifies the label to use for this field.
     *
     * @param labelFunction the function used to determine the effective label
     * @return the field itself for fluent method calls
     */
    public FieldDefinition withLabel(Supplier<String> labelFunction) {
        this.label = labelFunction;
        return this;
    }

    /**
     * Adds a check for the field.
     *
     * @param check the check to add
     * @return the field itself for fluent method calls
     */
    public FieldDefinition withCheck(ValueCheck check) {
        if (check != null) {
            this.checks.add(check);
        }
        return this;
    }

    /**
     * Adds an alias for the field.
     * <p>
     * Aliases are used by {@link ImportDictionary#determineMappingFromHeadings(Values, boolean)} to "learn" which
     * field is provided in which column.
     *
     * @param alias the alias to add
     * @return the field itself for fluent method calls
     */
    public FieldDefinition addAlias(String alias) {
        aliases.add(ImportDictionary.normalize(NLS.smartGet(alias)));

        return this;
    }

    /**
     * Verifies that a given value passes all provided checks.
     *
     * @param value the value to verify
     * @throws IllegalArgumentException if a check fails
     */
    public void verify(Value value) {
        this.checks.forEach(check -> check.perform(value));
    }

    /**
     * Returns the effective label for this field.
     *
     * @return the label to use for this field
     */
    public String getLabel() {
        return label.get();
    }

    /**
     * Returns the name of the field.
     *
     * @return the "technical" name of the field
     */
    public String getName() {
        return name;
    }

    /**
     * Returns a set of aliases for this field.
     *
     * @return the aliases of this field
     */
    public Set<String> getAliases() {
        return aliases == null ? Collections.emptySet() : Collections.unmodifiableSet(aliases);
    }

    /**
     * Lists all remarks to show for this field.
     *
     * @return a list of all remarks
     */
    public List<String> getRemarks() {
        List<String> result = new ArrayList<>();
        this.checks.stream().map(ValueCheck::generateRemark).filter(Objects::nonNull).collect(Lambdas.into(result));
        if (!aliases.isEmpty()) {
            result.add(NLS.fmtr("FieldDefinition.aliasRemark")
                          .set("aliases", aliases.stream().map(NLS::smartGet).collect(Collectors.joining(", ")))
                          .format());
        }

        return result;
    }

    /**
     * Returns the type description of the field.
     *
     * @return the type description to be shown to the user
     */
    public String getType() {
        return type;
    }
}
