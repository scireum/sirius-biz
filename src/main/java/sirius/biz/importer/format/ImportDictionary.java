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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Defines a record or dataset by declaring which fields are expected.
 * <p>
 * This can be used to load data (fields can provided aliases to be more flexible). Also the provided field definitions
 * can be used to verify an imported dataset.
 */
public class ImportDictionary {

    private static final String PARAM_INDEX = "index";
    private static final String PARAM_EXPECTED = "expected";
    private static final String PARAM_COLUMN = "column";
    private static final String PARAM_COLUMNS = "columns";
    private static final String PARAM_COUNT = "count";
    private static final String PARAM_FIELD = "field";
    private static final String PARAM_LABEL = "label";
    private static final String PARAM_MESSAGE = "message";
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
                                .set(PARAM_INDEX, index + 1)
                                .set(PARAM_COLUMN, headerName)
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
     * @param problemConsumer          the consumer to be supplied with all errors being detected
     * @param failForAdditionalColumns <tt>true</tt> to fail if there are "too many" column, <tt>false</tt> to
     *                                 simply ignore those
     * @return <tt>true</tt> if at least one problem was detected, <tt>false</tt> otherwise
     */
    public boolean detectHeaderProblems(Values header,
                                        Consumer<String> problemConsumer,
                                        boolean failForAdditionalColumns) {
        if (mappingFunction == null) {
            throw new IllegalStateException("Cannot verify headings as the mapping function hasn't been specified yet.");
        }

        AtomicBoolean problemDetected = new AtomicBoolean(false);

        Values headerFields = Values.of(header.asList()
                                              .stream()
                                              .map(String::valueOf)
                                              .map(field -> resolve(field).orElse(field))
                                              .collect(Collectors.toList()));

        Values mappingValues = Values.of(mappingFunction);
        AtomicInteger mappingIndex = new AtomicInteger(0);
        AtomicInteger headerIndex = new AtomicInteger(0);

        while (mappingIndex.get() < mappingValues.length()) {
            String expectedField = mappingValues.at(mappingIndex.get()).asString();
            String headerField = headerFields.at(headerIndex.get()).asString();

            if (Strings.isEmpty(expectedField) || Strings.areEqual(headerField, expectedField)) {
                // We have a match, advance both indices...
                mappingIndex.incrementAndGet();
                headerIndex.incrementAndGet();
            } else {
                problemDetected.set(true);

                if (Strings.isEmpty(headerField)) {
                    // We're out of header columns - report missing column...
                    problemConsumer.accept(NLS.fmtr("ImportDictionary.missingColumn")
                                              .set(PARAM_INDEX, headerIndex.get() + 1)
                                              .set(PARAM_EXPECTED, expectedField)
                                              .format());
                    mappingIndex.incrementAndGet();
                } else {
                    // Try to diagnose why the headers don't match...
                    diagnoseHeaderProblem(header,
                                          problemConsumer,
                                          headerFields,
                                          mappingValues,
                                          mappingIndex,
                                          headerIndex);
                }
            }
        }

        if (failForAdditionalColumns) {
            for (int superfluousIndex = headerIndex.get();
                 superfluousIndex < headerFields.length();
                 superfluousIndex++) {
                problemDetected.set(true);
                problemConsumer.accept(NLS.fmtr("ImportDictionary.superfluousColumn")
                                          .set(PARAM_INDEX, superfluousIndex + 1)
                                          .set(PARAM_COLUMN, header.at(superfluousIndex))
                                          .format());
            }
        }

        return problemDetected.get();
    }

    /**
     * Tries to diagnose why columns (mapping vs. headers) don't match.
     * <p>
     * The reason can be one of three things:
     * <ol>
     *     <li>There are superfluous columns in the headings - report those and sync the given indices again</li>
     *     <li>There are missing columns in the headings - report those and sync the given indices again</li>
     *     <li>A column is given, which is completely unexpected - report this mismatch and advance both indices by one</li>
     * </ol>
     *
     * @param header          the actual header values
     * @param problemConsumer the consumer to be supplied with all errors being detected
     * @param headerFields    the resolved header fields (after applying aliases)
     * @param mappingValues   the mapping values (expected columns)
     * @param mappingIndex    the current index in the mapping values as mutable reference
     * @param headerIndex     the current index in the header values as mutable reference
     */
    protected void diagnoseHeaderProblem(Values header,
                                         Consumer<String> problemConsumer,
                                         Values headerFields,
                                         Values mappingValues,
                                         AtomicInteger mappingIndex,
                                         AtomicInteger headerIndex) {
        String expectedField = mappingValues.at(mappingIndex.get()).asString();
        String headerField = headerFields.at(headerIndex.get()).asString();

        if (handleSuperfluousColumns(header, problemConsumer, headerFields, headerIndex, expectedField)) {
            return;
        }

        if (handleMissingColumns(problemConsumer, mappingValues, mappingIndex, headerField)) {
            return;
        }

        problemConsumer.accept(NLS.fmtr("ImportDictionary.wrongColumn")
                                  .set(PARAM_INDEX, mappingIndex.get() + 1)
                                  .set(PARAM_COLUMN, headerField)
                                  .set(PARAM_EXPECTED, expectedField)
                                  .format());

        mappingIndex.incrementAndGet();
        headerIndex.incrementAndGet();
    }

    /**
     * Determines if there are some missing columns in the header.
     * <p>
     * Tries to find the next header column matching <tt>headerField</tt>, if found, all intermediate columns are
     * reported as missing and the indices are synced to match again.
     * </p>
     *
     * @param problemConsumer the consumer to be supplied with all errors being detected
     * @param mappingValues   the mapping values (expected columns)
     * @param mappingIndex    the current index in the mapping values as mutable reference
     * @param headerField     the currently active header field
     * @return <tt>true</tt> if there was one or more missing columns detected, <tt>false</tt> otherwise
     */
    private boolean handleMissingColumns(Consumer<String> problemConsumer,
                                         Values mappingValues,
                                         AtomicInteger mappingIndex,
                                         String headerField) {
        int nextHeaderOccurrence = mappingValues.asList().indexOf(headerField);
        if (nextHeaderOccurrence <= mappingIndex.get()) {
            return false;
        }

        for (int missingIndex = mappingIndex.get(); missingIndex < nextHeaderOccurrence; missingIndex++) {
            problemConsumer.accept(NLS.fmtr("ImportDictionary.missingColumn")
                                      .set(PARAM_INDEX, missingIndex + 1)
                                      .set(PARAM_EXPECTED, mappingValues.at(missingIndex))
                                      .format());
        }
        mappingIndex.set(nextHeaderOccurrence);
        return true;
    }

    /**
     * Determines if there are some superfluous columns in the header.
     * <p>
     * Tries to find the next mapping column matching <tt>expectedField</tt>, if found, all intermediate header columns
     * are reported as superfluous and the indices are synced to match again.
     * </p>
     *
     * @param problemConsumer the consumer to be supplied with all errors being detected
     * @param headerFields    the resolved header fields (after applying aliases)
     * @param headerIndex     the current index in the header values as mutable reference
     * @param expectedField   the currently active (expected) mapping field
     * @return <tt>true</tt> if there was one or more missing columns detected, <tt>false</tt> otherwise
     */
    private boolean handleSuperfluousColumns(Values header,
                                             Consumer<String> problemConsumer,
                                             Values headerFields,
                                             AtomicInteger headerIndex,
                                             String expectedField) {
        int nextMappingOccurrence = headerFields.asList().indexOf(expectedField);
        if (nextMappingOccurrence <= headerIndex.get()) {
            return false;
        }

        for (int superfluousIndex = headerIndex.get(); superfluousIndex < nextMappingOccurrence; superfluousIndex++) {
            problemConsumer.accept(NLS.fmtr("ImportDictionary.superfluousColumn")
                                      .set(PARAM_INDEX, superfluousIndex + 1)
                                      .set(PARAM_COLUMN, header.at(superfluousIndex))
                                      .format());
        }

        headerIndex.set(nextMappingOccurrence);
        return true;
    }

    /**
     * Uses the previously determined <tt>mapping function</tt> to transform a row into a field map.
     *
     * @param row                    the row to parse
     * @param failForAdditionalCells <tt>true</tt> to fail if there are "too many" cells in the given row,
     *                               <tt>false</tt> to simply ignore those
     * @return the field map represented as {@link Context}
     */
    public Context load(Values row, boolean failForAdditionalCells) {
        if (row.length() > mappingFunction.size() && failForAdditionalCells) {
            throw Exceptions.createHandled()
                            .withNLSKey("ImportDictionary.tooManyColumns")
                            .set(PARAM_COUNT, row.length())
                            .set(PARAM_COLUMNS, Strings.join(mappingFunction, ", "))
                            .handle();
        }

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
     * @param record          the accessor function to the record to verify
     * @param problemConsumer the consumer to be supplied with all detected problems
     * @return <tt>true</tt> if at least one problem was detected, <tt>false</tt> otherwise
     */
    public boolean detectRecordProblems(Function<String, Value> record, Consumer<String> problemConsumer) {
        AtomicBoolean problemDetected = new AtomicBoolean(false);
        fields.values().forEach(field -> {
            try {
                field.verify(record.apply(field.getName()));
            } catch (IllegalArgumentException | HandledException e) {
                problemDetected.set(true);
                problemConsumer.accept(NLS.fmtr("ImportDictionary.fieldError")
                                          .set(PARAM_FIELD, field.getName())
                                          .set(PARAM_LABEL, field.getLabel())
                                          .set(PARAM_MESSAGE, e.getMessage())
                                          .format());
            } catch (Exception e) {
                problemDetected.set(true);
                problemConsumer.accept(NLS.fmtr("ImportDictionary.severeFieldError")
                                          .set(PARAM_FIELD, field.getName())
                                          .set(PARAM_LABEL, field.getLabel())
                                          .set(PARAM_MESSAGE, Exceptions.handle(Log.BACKGROUND, e).getMessage())
                                          .format());
            }
        });

        return problemDetected.get();
    }

    /**
     * Verifies that the given record fulfills the check given for each field.
     *
     * @param record          the record to verify
     * @param problemConsumer the consumer to be supplied with all detected problems
     * @return <tt>true</tt> if at least one problem was detected, <tt>false</tt> otherwise
     */
    public boolean detectRecordProblems(Context record, Consumer<String> problemConsumer) {
        return detectRecordProblems(record::getValue, problemConsumer);
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
