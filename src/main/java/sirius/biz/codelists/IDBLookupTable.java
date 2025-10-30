/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.jupiter.IDBTable;
import sirius.biz.jupiter.Jupiter;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a {@link LookupTable} based on a given {@link sirius.biz.jupiter.IDBTable}.
 * <p>
 * Note that the configuration can specify which field is the leading code field (<tt>code</tt> is the default). Also
 * the config can specify which field is the name of an entry (<tt>name</tt> is the default). The same applies to the
 * field used to provide a description for an entry which defaults to <tt>description</tt>.
 * <p>
 * Additionally the config can specify which additional code fields are searched when a code is normalized.
 * <p>
 * Furthermore, if the table contains a field "deprecated", these entries will still resolve and normalize like
 * normal entries, but will be ignored when suggesting values or when scanning the table. Therefore, it is best to
 * sort these entries to the end of the table.
 */
class IDBLookupTable extends LookupTable {

    private static final String CACHE_PREFIX_FETCH_FIELD = "fetch-field-";
    private static final String CACHE_PREFIX_COUNT = "count-";
    private static final String CACHE_PREFIX_FETCH_TRANSLATED_FIELD = "fetch-field-";
    private static final String CACHE_PREFIX_NORMALIZE = "normalize-";
    private static final String CACHE_PREFIX_NORMALIZE_WITH_MAPPING = "normalize-with-mapping";
    private static final String CACHE_PREFIX_REVERSE_LOOKUP = "reverse-lookup-";
    private static final String CACHE_PREFIX_FETCH_OBJECT = "fetch-object-";

    private static final String CONFIG_CODE_FIELD = "codeField";
    private static final String CONFIG_NAME_FIELD = "nameField";
    private static final String CONFIG_DESCRIPTION_FIELD = "descriptionField";
    private static final String CONFIG_ALIAS_CODE_FIELDS = "aliasCodeFields";

    protected final IDBTable table;
    protected final String codeField;
    protected final String nameField;
    protected final String descriptionField;
    protected final String aliasCodeFields;

    private record ColumnIndexAndName(int index, String columnName) {
    }

    private static final ColumnIndexAndName DEPRECATED = new ColumnIndexAndName(3, "deprecated");
    private static final ColumnIndexAndName PERMISSION = new ColumnIndexAndName(4, "permission");
    private static final ColumnIndexAndName SOURCE = new ColumnIndexAndName(5, ".");

    @Part
    @Nullable
    private static Jupiter jupiter;

    IDBLookupTable(Extension extension, IDBTable table) {
        super(extension);
        this.table = table;
        this.codeField = extension.get(CONFIG_CODE_FIELD).asString("code");
        this.nameField = extension.get(CONFIG_NAME_FIELD).asString("name");
        this.descriptionField = extension.get(CONFIG_DESCRIPTION_FIELD).asString("description");
        this.aliasCodeFields =
                Stream.concat(Stream.of(codeField), extension.getStringList(CONFIG_ALIAS_CODE_FIELDS).stream())
                      .distinct()
                      .collect(Collectors.joining(","));
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return performFetchField(code, codeField).isFilled();
    }

    @Override
    protected Optional<String> performResolveName(String code, String language) {
        return performFetchTranslatedField(code, nameField, language);
    }

    @Override
    protected Optional<String> performResolveDescription(String code, String language) {
        if (Strings.isEmpty(descriptionField)) {
            return Optional.empty();
        }
        return performFetchTranslatedField(code, descriptionField, language);
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_FETCH_FIELD
                                               + table.getName()
                                               + "-"
                                               + code
                                               + "-"
                                               + targetField,
                                               () -> table.query()
                                                          .lookupPaths(codeField)
                                                          .searchValue(code)
                                                          .singleRow(targetField)
                                                          .map(row -> row.at(0))
                                                          .filter(Value::isFilled)
                                                          .orElse(Value.EMPTY));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error on fetch field with code '%s' field '%s' table '%s': %s (%s)",
                                              code,
                                              targetField,
                                              table.getName())
                      .handle();
            return Value.EMPTY;
        }
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String language) {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_FETCH_TRANSLATED_FIELD
                                               + table.getName()
                                               + "-"
                                               + code
                                               + "-"
                                               + targetField
                                               + "-"
                                               + language,
                                               () -> table.query()
                                                          .lookupPaths(codeField)
                                                          .searchValue(code)
                                                          .translate(language)
                                                          .singleRow(targetField)
                                                          .map(row -> row.at(0).asString())
                                                          .filter(Strings::isFilled));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Error on fetch translated field with code '%s' field '%s' lang '%s' table '%s': %s (%s)",
                              code,
                              targetField,
                              language,
                              table.getName())
                      .handle();
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_NORMALIZE + table.getName() + "-" + code,
                                               () -> table.query()
                                                          .lookupPaths(aliasCodeFields)
                                                          .searchValue(code)
                                                          .singleRow(codeField)
                                                          .map(row -> row.at(0).asString())
                                                          .filter(Strings::isFilled));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error on normalize code '%s' table '%s': %s (%s)", code, table.getName())
                      .handle();
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(String code, String mapping) {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_NORMALIZE_WITH_MAPPING
                                               + table.getName()
                                               + "-"
                                               + code
                                               + "-"
                                               + mapping,
                                               () -> table.query()
                                                          .lookupPaths(mapping)
                                                          .searchValue(code)
                                                          .singleRow(codeField)
                                                          .map(row -> row.at(0).asString())
                                                          .filter(Strings::isFilled));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error on normalize code '%s' mapping '%s' table '%s': %s (%s)",
                                              code,
                                              mapping,
                                              table.getName())
                      .handle();
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_REVERSE_LOOKUP + table.getName() + "-" + name,
                                               () -> table.query()
                                                          .lookupPaths(nameField)
                                                          .searchValue(name.toLowerCase())
                                                          .singleRow(codeField)
                                                          .map(row -> row.at(0).asString())
                                                          .filter(Strings::isFilled));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error on reverse lookup name '%s' table '%s': %s (%s)",
                                              name,
                                              table.getName())
                      .handle();
            return Optional.empty();
        }
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        if (useCache) {
            return jupiter.fetchFromLargeCache(CACHE_PREFIX_FETCH_OBJECT + table.getName() + "-" + code,
                                               () -> fetchObjectFromIDB(type, code));
        } else {
            return fetchObjectFromIDB(type, code);
        }
    }

    private <T> Optional<T> fetchObjectFromIDB(Class<T> type, String code) {
        try {
            return table.query()
                        .lookupPaths(codeField)
                        .searchValue(code).singleRow(SOURCE.columnName())
                        .map(row -> makeObject(type, Json.parseObject(row.at(0).asString())));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error on fetch object type '%s' code '%s' table '%s': %s (%s)",
                                              type.getName(),
                                              code,
                                              table.getName())
                      .handle();
            return Optional.empty();
        }
    }

    protected <T> T makeObject(Class<T> type, ObjectNode jsonData) {
        try {
            Constructor<T> constructor = type.getConstructor(ObjectNode.class);
            return constructor.newInstance(jsonData);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Cannot create a payload object for %s - A public accessible constructor accepting a value is required!",
                    exception);
        }
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit,
                                                      String searchTerm,
                                                      String language,
                                                      boolean considerDeprecatedValues) {
        try {
            return table.query()
                        .searchInAllFields()
                        .searchValue(searchTerm)
                        .translate(language)
                        .allRows(codeField,
                                 nameField,
                                 descriptionField,
                                 DEPRECATED.columnName(),
                                 PERMISSION.columnName())
                        .filter(row -> filterDeprecatedValues(considerDeprecatedValues, row))
                        .filter(this::filterRequiredPermission)
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                        .map(row -> new LookupTableEntry(row.at(0).asString(),
                                                         row.at(1).asString(),
                                                         row.at(2).getString()));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error on suggest searchterm '%s' lang '%s' table '%s': %s (%s)",
                                              searchTerm,
                                              language,
                                              table.getName())
                      .handle();
            return Stream.empty();
        }
    }

    @Override
    public Stream<LookupTableEntry> scan(String language, Limit limit, boolean considerDeprecatedValues) {
        try {
            return table.query()
                        .translate(language)
                        .allRows(codeField,
                                 nameField,
                                 descriptionField,
                                 DEPRECATED.columnName(),
                                 PERMISSION.columnName(),
                                 SOURCE.columnName())
                        .filter(row -> filterDeprecatedValues(considerDeprecatedValues, row))
                        .filter(this::filterRequiredPermission)
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                        .map(this::processSearchOrScanRow);
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error scanning lang '%s' table '%s': %s (%s)", language, table.getName())
                      .handle();
            return Stream.empty();
        }
    }

    private boolean filterRequiredPermission(Values row) {
        return UserContext.getCurrentUser().hasPermission(row.at(PERMISSION.index()).asString());
    }

    private boolean filterDeprecatedValues(boolean considerDeprecatedValues, Values row) {
        return considerDeprecatedValues || row.at(DEPRECATED.index()).asLong(0) != 1L;
    }

    @Override
    public Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language) {
        try {
            return table.query()
                        .searchInAllFields()
                        .searchValue(searchTerm)
                        .translate(language)
                        .allRows(codeField,
                                 nameField,
                                 descriptionField,
                                 DEPRECATED.columnName(),
                                 PERMISSION.columnName(),
                                 SOURCE.columnName())
                        .filter(row -> filterRequiredPermission(row))
                        .map(this::processSearchOrScanRow)
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Error scanning lang '%s' table '%s': %s (%s)", language, table.getName())
                      .handle();
            return Stream.empty();
        }
    }

    private LookupTableEntry processSearchOrScanRow(Values row) {
        LookupTableEntry entry =
                new LookupTableEntry(row.at(0).asString(), row.at(1).asString(), row.at(2).getString());
        if (row.at(DEPRECATED.index()).asLong(0) == 1L) {
            entry.markDeprecated();
        }
        if (row.at(SOURCE.index()).isFilled()) {
            try {
                entry.withSource(Json.parseObject(row.at(SOURCE.index()).asString()));
            } catch (Exception exception) {
                Exceptions.ignore(exception);
            }
        }

        return entry;
    }

    @Override
    public int count() {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_COUNT + table.getName(), table::size);
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Failed to fetch entry count of table '%s': %s (%s)", table.getName())
                      .handle();
            return 0;
        }
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue) {
        try {
            return table.query()
                        .translate(language)
                        .lookupPaths(lookupPath)
                        .searchValue(lookupValue)
                        .allRows(codeField,
                                 nameField,
                                 descriptionField,
                                 DEPRECATED.columnName(),
                                 PERMISSION.columnName())
                        .filter(row -> filterDeprecatedValues(false, row))
                        .filter(this::filterRequiredPermission)
                        .map(row -> new LookupTableEntry(row.at(0).asString(),
                                                         row.at(1).asString(),
                                                         row.at(2).getString()));
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Error on lookup scan lang '%s' lookupPath '%s' lookupValue '%s' table '%s': %s (%s)",
                              language,
                              lookupPath,
                              lookupValue,
                              table.getName())
                      .handle();
            return Stream.empty();
        }
    }
}
