/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Defines a record or dataset by declaring which fields are expected.
 * <p>
 * This can be used to load data (fields can provided aliases to be more flexible). Also the provided field definitions
 * can be used to verify an imported dataset.
 */
public class ImportDictionary {

    private Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    private Map<String, String> aliases = new LinkedHashMap<>();
    private List<String> mappingFunction;

    /**
     * Adds a field to the record.
     * <p>
     * Note that this cannot be done once the mapping function has been defined.
     *
     * @param field the field to add
     * @return the instance itself for fluent method calls
     */
    public ImportDictionary addField(FieldDefinition field) {
        if (mappingFunction != null) {
            throw new IllegalStateException("Cannot add fields once the mapping function has been set.");
        }

        if (fields.containsKey(field.getName())) {
            throw new IllegalArgumentException(Strings.apply("A field named '%s' is already present!",
                                                             field.getName()));
        }

        this.fields.put(field.getName(), field);

        for (String alias : field.getAliases()) {
            if (aliases.containsKey(alias)) {
                throw new IllegalArgumentException(Strings.apply("An alias named '%s' is already present!", alias));
            }

            aliases.put(alias, field.getName());
        }

        return this;
    }

    /**
     * Returns a list of all fields in this record.
     * <p>
     * Note that this is not neccessarily in the expected order. Use the <tt>mapping function</tt> to specify the order.
     *
     * @return the fields in this dictionary
     */
    public List<FieldDefinition> getFields() {
        return Collections.unmodifiableList(new ArrayList<>(fields.values()));
    }

    /**
     * Imports all fields from the given dictionary.
     *
     * @param other the dictionary to import the fields from
     * @return the instance itself for fluent method calls
     */
    public ImportDictionary copyFieldsFrom(ImportDictionary other) {
        other.fields.values().forEach(this::addField);
        return this;
    }

    /**
     * Resets the <tt>mapping function</tt>.
     */
    public void resetMappings() {
        this.mappingFunction = null;
    }

    /**
     * Determines if the <tt>mapping function</tt> has already been specified.
     *
     * @return <tt>true</tt> if the mapping is available, <tt>false</tt> otherwise
     */
    public boolean hasMappings() {
        return mappingFunction != null;
    }

    /**
     * Returns the mapping function (order of fields).
     *
     * @return the mapping function which is currently used or an empty list if the mapping hasn't been specified yet
     */
    public List<String> getMappings() {
        if (mappingFunction == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(mappingFunction);
    }

    /**
     * Uses the order of the fields as mapping.
     *
     * @return the instance itself for fluent method calls
     */
    public ImportDictionary useIdentityMapping() {
        this.mappingFunction = new ArrayList<>(fields.keySet());
        return this;
    }

    /**
     * Determines the <tt>mapping function</tt> by using the field aliases and the given header row.
     *
     * @param header                the row which contains the column headers
     * @param failForUnknownColumns <tt>true</tt> to fail if there is an unknown column. <tt>false</tt> to simply
     *                              ignore this column
     * @return the instance itself for fluent method calls
     */
    public ImportDictionary determineMappingFromHeadings(Values header, boolean failForUnknownColumns) {
        this.mappingFunction = new ArrayList<>();
        for (int index = 0; index < header.length(); index++) {
            String headerName = header.at(index).asString();
            String headerField = resolve(headerName).orElse(null);
            if (failForUnknownColumns && headerField == null) {
                throw Exceptions.createHandled()
                                .withNLSKey("ImportDictionary.unknownColumn")
                                .set("index", index + 1)
                                .set("column", headerName)
                                .handle();
            }
            this.mappingFunction.add(headerField);
        }

        return this;
    }

    /**
     * Specifies the <tt>mapping function</tt> to use.
     *
     * @param mappingFunction the expected order of fields when importing data.
     * @return the instance itself for fluent method calls
     */
    public ImportDictionary useMapping(List<String> mappingFunction) {
        this.mappingFunction = new ArrayList<>(mappingFunction);
        return this;
    }

    /**
     * Verifies the given header row against the specified <tt>mapping function</tt>.
     *
     * @param header                   the row which contains the column headings
     * @param failForAdditionalColumns <tt>true</tt> to fail if there are "too many" column, <tt>false</tt> to
     *                                 simply ignore those
     * @throws sirius.kernel.health.HandledException in case of an invalid header row
     */
    public void verifyHeadings(Values header, boolean failForAdditionalColumns) {
        if (mappingFunction == null) {
            throw new IllegalStateException("Cannot verify headings as the mapping function hasn't been specified yet.");
        }

        for (int index = 0; index < Math.min(mappingFunction.size(), header.length()); index++) {
            String headerName = header.at(index).asString();
            String headerField = resolve(headerName).orElse(headerName);
            String expectedField = mappingFunction.get(index);
            if (expectedField != null && !Strings.areEqual(headerField, expectedField)) {
                throw Exceptions.createHandled()
                                .withNLSKey("ImportDictionary.wrongColumn")
                                .set("index", index + 1)
                                .set("column", headerName)
                                .set("expected", expectedField)
                                .handle();
            }
        }

        if (header.length() < mappingFunction.size()) {
            throw Exceptions.createHandled()
                            .withNLSKey("ImportDictionary.tooFewColumns")
                            .set("count", header.length())
                            .set("columns", Strings.join(mappingFunction, ", "))
                            .handle();
        }
        if (header.length() > mappingFunction.size() && failForAdditionalColumns) {
            throw Exceptions.createHandled()
                            .withNLSKey("ImportDictionary.tooManyColumns")
                            .set("count", header.length())
                            .set("columns", Strings.join(mappingFunction, ", "))
                            .handle();
        }
    }

    /**
     * Uses the previously determined <tt>mapping function</tt> to transform a row into a field map.
     *
     * @param row the row to parse
     * @return the field map represented as {@link Context}
     */
    public Context load(Values row) {
        Context result = new Context();
        for (int index = 0; index < mappingFunction.size(); index++) {
            String fieldName = mappingFunction.get(index);
            if (fieldName != null) {
                result.put(fieldName, row.at(index).get());
            }
        }

        return result;
    }

    /**
     * Verifies that the given record fulfills the check given for each field.
     *
     * @param record the accessor function to the record to verify
     * @throws sirius.kernel.health.HandledException in case if invalid data
     */
    public void verifyRecord(Function<String, Value> record) {
        fields.values().forEach(field -> {
            try {
                field.verify(record.apply(field.getName()));
            } catch (HandledException e) {
                throw Exceptions.createHandled()
                                .withNLSKey("ImportDictionary.fieldError")
                                .set("field", field.getName())
                                .set("label", field.getLabel())
                                .set("message", e.getMessage())
                                .handle();
            } catch (Exception e) {
                throw Exceptions.createHandled()
                                .withNLSKey("ImportDictionary.severeFieldError")
                                .set("field", field.getName())
                                .set("label", field.getLabel())
                                .set("message", Exceptions.handle(Log.BACKGROUND, e).getMessage())
                                .handle();
            }
        });
    }

    /**
     * Verifies that the given record fulfills the check given for each field.
     *
     * @param record the record to verify
     * @throws sirius.kernel.health.HandledException in case if invalid data
     */
    public void verifyRecord(Context record) {
        verifyRecord(record::getValue);
    }

    /**
     * Resolves the given field (name) into an effective field by applying the known aliases.
     *
     * @param field the fieldname to search by
     * @return the effective field or an empty optional if no matching alias is present
     */
    public Optional<String> resolve(String field) {
        if (Strings.isEmpty(field)) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliases.get(normalize(field)));
    }

    /**
     * Normalizes an alias or fieldname to simplify comparing them.
     * <p>
     * This will remove everything except characters, digits and "_". All characters will be lowercased.
     *
     * @param field the field to normalize
     * @return the normalized version of the field
     */
    protected static String normalize(String field) {
        return field.toLowerCase()
                    .replace("ä", "ae")
                    .replace("ö", "oe")
                    .replace("ü", "ue")
                    .replace("ß", "ss")
                    .replaceAll("[^a-z0-9_]", "");
    }

    /**
     * Returns the <tt>mapping function</tt> as string.
     *
     * @return a string representation of the mapping function
     */
    public String getMappingAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(NLS.get("ImportDictionary.mappingFunction"));
        sb.append("\n");
        sb.append("----------------------------------------\n");
        for (int i = 0; i < mappingFunction.size(); i++) {
            sb.append(i + 1);
            sb.append(": ");
            sb.append(mappingFunction.get(i));
            sb.append("\n");
        }

        return sb.toString();
    }
}
