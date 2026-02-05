/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    protected String typeUrl;
    protected Supplier<String> label;
    protected boolean hidden;
    protected Set<String> aliases = new HashSet<>();
    protected List<ValueCheck> checks = new ArrayList<>();
    protected boolean appendContextInErrorMessage;

    /**
     * Creates a new field with the given name and type.
     *
     * @param name the name of the field
     * @param type the type description. Use helpers like {@link #typeString(Integer)} to generate one
     */
    public FieldDefinition(String name, String type) {
        this.name = name;
        this.type = type;
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
     * Boilerplate to create a new string field without length.
     *
     * @param name the name of the field
     * @return the newly created field
     */
    public static FieldDefinition stringField(String name) {
        return new FieldDefinition(name, typeString(null));
    }

    /**
     * Boilerplate to create a new string field with the given <tt>maxLength</tt> which is also enforced by a
     * {@link LengthCheck}.
     *
     * @param name      the name of the field
     * @param maxLength the maximal length of the string
     * @return the newly created field
     */
    public static FieldDefinition stringField(String name, int maxLength) {
        return new FieldDefinition(name, typeString(maxLength)).withCheck(new LengthCheck(maxLength));
    }

    /**
     * Boilerplate to create a new string field with a given list of permitted values.
     *
     * @param name       then name of the field
     * @param enumValues the list of permitted values
     * @return the newly created field
     */
    public static FieldDefinition enumStringField(String name, List<String> enumValues) {
        return new FieldDefinition(name, typeString(null)).withCheck(new ValueInListCheck(enumValues));
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
     * Boilerplate to create a new numeric field without precision or scale.
     *
     * @param name the name of the field
     * @return the newly created field
     */
    public static FieldDefinition numericField(String name) {
        return new FieldDefinition(name, typeNumber(0, 0));
    }

    /**
     * Boilerplate to create a new numeric field with the given <tt>precision</tt> and <tt>scale</tt>} using fixed point
     * arithmetics for the precision check.
     *
     * @param name      the name of the field
     * @param precision the precision of the field
     * @param scale     the scale of the field
     * @return the newly created field
     * @deprecated use {@link #numericField(String, int, int, boolean)} instead
     */
    @Deprecated
    public static FieldDefinition numericField(String name, int precision, int scale) {
        return new FieldDefinition(name, typeNumber(precision, scale)).withCheck(new AmountScaleCheck(precision,
                                                                                                      scale));
    }

    /**
     * Boilerplate to create a new numeric field with the given <tt>precision</tt> and <tt>scale</tt>}.
     *
     * @param name                  the name of the field
     * @param precision             the precision of the field
     * @param scale                 the scale of the field
     * @param useArbitraryPrecision if <tt>true</tt>, arbitrary precision is used instead of fixed point arithmetics
     * @return the newly created field
     */
    public static FieldDefinition numericField(String name, int precision, int scale, boolean useArbitraryPrecision) {
        AmountScaleCheck check = new AmountScaleCheck(precision, scale);
        if (useArbitraryPrecision) {
            check.useArbitraryPrecision();
        }
        return new FieldDefinition(name, typeNumber(precision, scale)).withCheck(check);
    }

    /**
     * Helper to create a type description for a string list field.
     *
     * @return a description to be shown to the user
     */
    public static String typeStringList() {
        return NLS.get("FieldDefinition.typeStringList");
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
     * Boilerplate to create a new boolean field.
     *
     * @param name the name of the field
     * @return the newly created field
     */
    public static FieldDefinition booleanField(String name) {
        return new FieldDefinition(name, typeBoolean()).withCheck(new ValueInListCheck("true",
                                                                                       "false",
                                                                                       "$" + NLS.CommonKeys.YES.key(),
                                                                                       "$" + NLS.CommonKeys.NO.key()));
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
     * Helper to create a type description for a date+time field.
     *
     * @return a description to be shown to the user
     */
    public static String typeDateTime() {
        return NLS.get("FieldDefinition.typeDateTime");
    }

    /**
     * Helper to create a type description for a time field.
     *
     * @return a description to be shown to the user
     */
    public static String typeTime() {
        return NLS.get("FieldDefinition.typeTime");
    }

    /**
     * Boilerplate to create a new date field without format.
     *
     * @param name the name of the field
     * @return the newly created field
     */
    public static FieldDefinition dateField(String name) {
        return new FieldDefinition(name, typeDate());
    }

    /**
     * Boilerplate to create a new date field with the given <tt>format</tt> which is enforced by {@link DateTimeFormatCheck}.
     *
     * @param name   the name of the field
     * @param format the date format of the field
     * @return the newly created field
     */
    public static FieldDefinition dateField(String name, String format) {
        return new FieldDefinition(name, typeDate()).withCheck(new DateTimeFormatCheck(format));
    }

    /**
     * Helper to create a type description for a field with a value from a LookupTable.
     *
     * @return a description to be shown to the user
     */
    public static String typeLookupValueProperty() {
        return NLS.get("FieldDefinition.typeLookupValueProperty");
    }

    /**
     * Helper to create a type description for a field with values from a LookupTable.
     *
     * @return a description to be shown to the user
     */
    public static String typeLookupValuesProperty() {
        return NLS.get("FieldDefinition.typeLookupValuesProperty");
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
     * @param constantLabel the label to show, which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated
     * @return the field itself for fluent method calls
     */
    public FieldDefinition withLabel(String constantLabel) {
        this.label = () -> NLS.smartGet(constantLabel);
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
     * Specifies the url to use for this field.
     *
     * @param typeUrl the url to link for this type
     * @return the field itself for fluent method calls
     */
    public FieldDefinition withTypeUrl(String typeUrl) {
        this.typeUrl = typeUrl;
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
     * Boilerplate to add a {@link RequiredCheck} to this field.
     *
     * @return the field itself for fluent method calls
     */
    public FieldDefinition markRequired() {
        return withCheck(new RequiredCheck());
    }

    /**
     * Marks the current field definition to be included in error messages with name and value for row identification purposes.
     *
     * @return the field itself for fluent method calls
     */
    public FieldDefinition appendContextInErrorMessage() {
        this.appendContextInErrorMessage = true;
        return this;
    }

    /**
     * Returns the info if this field's name and value should be included in error messages.
     *
     * @return true if this field's name and value should be included in error messages, false otherwise
     */
    public boolean shouldAppendContextInErrorMessage() {
        return appendContextInErrorMessage;
    }

    /**
     * Adds an alias for the field.
     * <p>
     * Aliases are used by {@link ImportDictionary#determineMappingFromHeadings(Values, boolean)} to "learn" which
     * field is provided in which column.
     *
     * @param alias the alias to add, which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     * @return the field itself for fluent method calls
     */
    public FieldDefinition addAlias(String alias) {
        aliases.add(NLS.smartGet(alias));
        return this;
    }

    /**
     * Removes an alias for the field.
     *
     * @param alias the alias to remove
     */
    public void removeAlias(String alias) {
        aliases.remove(alias);
    }

    /**
     * Adds an alias for the field resolving all available translations.
     * <p>
     * Aliases are used by {@link ImportDictionary#determineMappingFromHeadings(Values, boolean)} to "learn" which
     * field is provided in which column.
     * <p>
     * Opposed to {@link #addAlias(String)}, this method resolves all available translations and not only
     * the one in the current language context.
     *
     * @param alias the alias to add, which will be expanded to all available translations
     * @return the field itself for fluent method calls
     */
    public FieldDefinition addTranslatedAliases(@Nonnull String alias) {
        if (alias.charAt(0) != '$') {
            aliases.add(alias);
            return this;
        }

        UserContext.getCurrentScope()
                   .getDisplayLanguages()
                   .stream()
                   .map(language -> NLS.smartGet(alias, language))
                   .filter(text -> !text.equals(getLabel()))
                   .filter(text -> !aliases.stream()
                                           .map(String::toLowerCase)
                                           .collect(Collectors.toSet())
                                           .contains(text.toLowerCase()))
                   .distinct()
                   .forEach(aliases::add);

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
        return Objects.requireNonNullElse(getRawLabel(), name);
    }

    /**
     * Returns the real label for this field.
     *
     * @return the real label to use for this field
     */
    @Nullable
    public String getRawLabel() {
        if (label == null) {
            return null;
        }

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
        return Collections.unmodifiableSet(aliases);
    }

    /**
     * Lists all remarks to show for this field.
     *
     * @return a list of all remarks
     */
    public List<String> getRemarks() {
        List<String> result = this.checks.stream()
                                         .map(ValueCheck::generateRemark)
                                         .filter(Objects::nonNull)
                                         .collect(Collectors.toList());
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

    /**
     * Returns the url to link for this type.
     *
     * @return the url to link for this type
     */
    public String getTypeUrl() {
        return typeUrl;
    }

    /**
     * Hides this field from the documentation.
     */
    public void hide() {
        this.hidden = true;
    }

    /**
     * Determines if the field is hidden from the documentation.
     *
     * @return <tt>true</tt> if the field is hidden, <tt>false</tt> otherwise
     */
    public boolean isHidden() {
        return hidden;
    }
}
