/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a table of master data which maps one or more code fields to one or more payload values.
 * <p>
 * Lookup tables are actually an abstraction over {@link CodeLists} and {@link sirius.biz.jupiter.IDBTable tables}
 * provided by Jupiter. Based on the system config, a lookup table can be represented by either of those.
 * <p>
 * A lookup table has a normalized code field, which is used e.g. in a database entity to represent a value. Using
 * {@link #normalize(String)} one can transform aliased codes (e.g. a three-letter ISO language code) into the main
 * code (e.g. a two-letter ISO code). This can also be used to verify that a code is valid.
 * <p>
 * Next to the code there is always a name field which is used to provide a textual representation of the field. This
 * can be automatically translated to the users' language.
 * <p>
 * Using {@link #fetchField(String, String)}, {@link #fetchTranslatedField(String, String)} or even
 * {@link #fetchObject(Class, String)} additional values can be resolved (esp. when using Jupiter).
 * <p>
 * Referencing lookup tables is further simplified by {@link LookupValue} which can be directly embedded into
 * database entities and &lt;lookupField&gt; which can be used to render an autocomplete along with a value selection
 * helper for said fields.
 * <p>
 * To actually control how a lookup table is represented internally, a config section within <tt>lookup-tables</tt>
 * has to be defined. See <tt>component-biz.conf</tt> for further details. If no matching section is found, the
 * {@link CodeLists code list} with the same name is used.
 */
public abstract class LookupTable {

    /**
     * Defines the code used to represent texts valid in all languages.
     */
    public static final String FALLBACK_LANGUAGE_CODE = "xx";

    private static final int MAX_SUGGESTIONS = 25;
    private static final String CONFIG_KEY_CODE_CASE_MODE = "codeCase";
    public static final String CONFIG_KEY_MAPPING_FIELD = "mappingsField";
    private final String mappingsField;

    protected enum CodeCase {
        LOWER, UPPER, VERBATIM
    }

    protected final CodeCase codeCase;
    protected final Extension extension;

    protected LookupTable(Extension extension) {
        this(extension,
             extension.get(CONFIG_KEY_CODE_CASE_MODE).upperCase().getEnum(CodeCase.class).orElse(CodeCase.VERBATIM));
    }

    protected LookupTable(Extension extension, CodeCase codeCase) {
        this.extension = extension;
        this.mappingsField = extension.get(CONFIG_KEY_MAPPING_FIELD).asString();
        this.codeCase = codeCase;
    }

    /**
     * Normalizes a given code.
     * <p>
     * Depending on the <tt>codeCase</tt> this will change the given code to upper- or lowercase.
     *
     * @param code the code to normalize
     * @return the normalized code (all uppercase or lowercase - depending on the settings for the lookup table)
     */
    @Nullable
    public String normalizeCodeValue(@Nullable String code) {
        if (Strings.isEmpty(code)) {
            return null;
        }

        return switch (codeCase) {
            case LOWER -> code.toLowerCase();
            case UPPER -> code.toUpperCase();
            default -> code;
        };
    }

    /**
     * Attempts to resolve the name for the given code or returns the input itself.
     *
     * @param code the code to resolve the name for
     * @return the name for the given code in the currently active language if present or the code itself. Note that
     * this will only resolve the main code. When in doubt, the code must be normalized via {@link #normalize(String)}
     * before invoking this method.
     */
    public String forceResolveName(String code) {
        return resolveName(code).orElse(code);
    }

    /**
     * Attempts to resolve the name for the given code or returns the input itself.
     *
     * @param code     the code to resolve the name for
     * @param language the language of the name to resolve
     * @return the name for the given code in the given language if present or the code itself. Note that this will only
     * resolve the main code. When in doubt, the code must be normalized via {@link #normalize(String)} before invoking
     * this method.
     */
    public String forceResolveName(String code, String language) {
        return resolveName(code, language).orElse(code);
    }

    /**
     * Resolves the name for the given code.
     *
     * @param code the code to resolve the name for
     * @return the name for the given code in the currently active language or an empty optional, if the code
     * is unknown. Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> resolveName(String code) {
        return resolveName(code, NLS.getCurrentLanguage());
    }

    /**
     * Resolves the name in the given language for the given code.
     *
     * @param code     the code to resolve the name for
     * @param language the language of the name to resolve
     * @return the name for the given code in the given language or an empty optional, if the code is unknown.
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> resolveName(String code, String language) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performResolveName(normalizeCodeValue(code), language);
    }

    protected abstract Optional<String> performResolveName(@Nonnull String code, String language);

    /**
     * Determines if the table contains the given code.
     *
     * @param code the code to check
     * @return <tt>true</tt> if the code is contained in the table, <tt>false</tt> otherwise
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public boolean contains(String code) {
        if (Strings.isEmpty(code)) {
            return false;
        }

        return performContains(normalizeCodeValue(code));
    }

    protected abstract boolean performContains(@Nonnull String code);

    /**
     * Resolves the description for the given code.
     *
     * @param code the code to resolve the description for
     * @return the description for the given code in the currently active language or an empty optional, if the code
     * is unknown or no description is present.
     * <p>
     * Note that this will only resolve the main code. When in doubt, the code
     * must be normalized via {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> resolveDescription(String code) {
        return resolveDescription(code, NLS.getCurrentLanguage());
    }

    /**
     * Resolves the description in the given language for the given code.
     *
     * @param code     the code to resolve the description for
     * @param language the language of the description to resolve
     * @return the description for the given code in the given language or an empty optional, if the code is unknown or
     * if no description is present.
     * <p>
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> resolveDescription(String code, String language) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performResolveDescription(normalizeCodeValue(code), language);
    }

    protected abstract Optional<String> performResolveDescription(@Nonnull String code, String language);

    /**
     * Fetches the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @return the value of the field, or an empty optional if the code is unknown. Note that this will only resolve the
     * main code. When in doubt, the code must be normalized via {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> fetchField(String code, String targetField) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performFetchField(normalizeCodeValue(code), targetField).asOptionalString();
    }

    /**
     * Fetches the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @return the value of the field, or an empty value if the code is unknown. Note that this will only resolve the
     * main code. When in doubt, the code must be normalized via {@link #normalize(String)} before invoking this method.
     */
    public Value fetchFieldValue(String code, String targetField) {
        if (Strings.isEmpty(code)) {
            return Value.EMPTY;
        }

        return performFetchField(normalizeCodeValue(code), targetField);
    }

    /**
     * Fetches the requested field for the given code.
     * <p>
     * This interprets <tt>1</tt> as <tt>true</tt> in case the underlying table doesn't know booleans and instead stores
     * integers.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @return the boolean value of the field, or an empty optional if the code is unknown. Note that this will only
     * resolve the main code. When in doubt, the code must be normalized via {@link #normalize(String)} before invoking
     * this method.
     */
    public Optional<Boolean> fetchFieldBoolean(String code, String targetField) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        Value fieldValue = performFetchField(normalizeCodeValue(code), targetField);

        if (fieldValue.isNumeric()) {
            return fieldValue.map(value -> value.asInt(0) == 1);
        }

        return fieldValue.asOptional(Boolean.class);
    }

    /**
     * Fetches the mapping for the given code.
     * <p>
     * Mappings are most probably only supported by IDB backed tables. If <tt>acme</tt> is given, this will
     * return the value in <tt>mappings.acme</tt>.
     *
     * @param code    the code to fetch the mapping for
     * @param mapping the name of the mapping to fetch
     * @return the value to use, or an empty optional if either no mapping is present, or the code is unknown
     * @see #fetchMappingOrCode(String, String)
     */
    public Optional<String> fetchMapping(String code, String mapping) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return pullFirstValue(performFetchField(normalizeCodeValue(code),
                                                mappingsField + "." + mapping)).asOptionalString();
    }

    private Value pullFirstValue(Value possibleCollection) {
        if ((possibleCollection instanceof Collection<?> values) && (!values.isEmpty())) {
            return Value.of(values.iterator().next());
        } else {
            return possibleCollection;
        }
    }

    /**
     * Fetches the mapping for the given code or the code itself if no mapping is present or if the code is unknown.
     *
     * @param code    the code to fetch the mapping for
     * @param mapping the name of the mapping to fetch
     * @return either the mapping, or the code itself if no mapping is present
     * @see #fetchMapping(String, String)
     */
    @Nullable
    public String fetchMappingOrCode(String code, String mapping) {
        if (Strings.isEmpty(code)) {
            return null;
        }

        return fetchMapping(code, mapping).orElseGet(() -> normalizeCodeValue(code));
    }

    /**
     * Fetches either the primary or the secondary mapping stored for the given code.
     * <p>
     * This can be used to perform a two stage lookup. E.g. if an <tt>acme 1.0</tt> standard is requested, one could
     * query <tt>acme-10</tt> as primary mapping and <tt>acme</tt> as secondary. This way, if a version dependent
     * mapping is present, this will be used. Otherwise, the base value from the standard is returned (if present).
     *
     * @param code             the code to fetch the mapping for
     * @param primaryMapping   the more specific mapping to attempt to fetch
     * @param secondaryMapping the more general mapping to fetch
     * @return either the mapping for the first or second mapping to use or, an empty optional if neither is present
     */
    public Optional<String> fetchMappings(String code, String primaryMapping, String secondaryMapping) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        Optional<String> result = pullFirstValue(performFetchField(normalizeCodeValue(code),
                                                                   mappingsField
                                                                   + "."
                                                                   + primaryMapping)).asOptionalString();
        if (result.isPresent()) {
            return result;
        }

        return pullFirstValue(performFetchField(normalizeCodeValue(code),
                                                mappingsField + "." + secondaryMapping)).asOptionalString();
    }

    /**
     * Fetches the mapping for the given code or the code itself if no mapping is present or if the code is unknown.
     *
     * @param code             the code to fetch the mapping for
     * @param primaryMapping   the more specific mapping to attempt to fetch
     * @param secondaryMapping the more general mapping to fetch
     * @return either the mapping for the first or second mapping to use or the code itself if neither is present
     * @see #fetchMappings(String, String, String)
     */
    @Nullable
    public String fetchMappingsOrCode(String code, String primaryMapping, String secondaryMapping) {
        if (Strings.isEmpty(code)) {
            return null;
        }

        return fetchMappings(code, primaryMapping, secondaryMapping).orElseGet(() -> normalizeCodeValue(code));
    }

    protected abstract Value performFetchField(@Nonnull String code, String targetField);

    /**
     * Fetches the translated value of the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @return the translated value of the field using the current language, or an empty optional if the code is unknown.
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> fetchTranslatedField(String code, String targetField) {
        return fetchTranslatedField(code, targetField, NLS.getCurrentLanguage());
    }

    /**
     * Fetches the translated value of the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @param language    the language used to translate the resulting value
     * @return the translated value of the field using the given language, or an empty optional if the code is unknown.
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> fetchTranslatedField(String code, String targetField, String language) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performFetchTranslatedField(normalizeCodeValue(code), targetField, language);
    }

    protected abstract Optional<String> performFetchTranslatedField(@Nonnull String code,
                                                                    String targetField,
                                                                    String language);

    /**
     * Normalizes the given code into the main code used by this table.
     * <p>
     * Lookup tables will most often provide multiple codes for the same entry (e.g. two letter and three-letter ISO
     * codes for countries). This method checks all code and alias fields and resolves the given code into the leading
     * code used by this table, so it can be used in the other methods provided.
     * <p>
     * Note that this method can also be used to verify if a code is valid at all.
     *
     * @param code the code to normalize
     * @return the normalized (leading) code or an empty optional if the code or alias is unknown
     */
    public Optional<String> normalize(String code) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performNormalize(normalizeCodeValue(code));
    }

    protected abstract Optional<String> performNormalize(@Nonnull String code);

    /**
     * Attempts to normalize the given code or returns the input itself.
     *
     * @param code the code to normalize
     * @return the normalized code, or the original input which has been adjusted using
     * {@link #normalizeCodeValue(String)}
     */
    public String forceNormalize(String code) {
        return normalize(code).orElseGet(() -> normalizeCodeValue(code));
    }

    /**
     * Provides a comparator which sorts codes by the occurrence order within the list.
     *
     * @param field the field to sort by. This can be any numeric field. Note that the YAML loader for Jupiter can
     *              generate such fields when specifying a <tt>rowNumber</tt>.
     *              See: <a href="https://docs.rs/jupiter/latest/jupiter/idb/idb_yaml_loader/index.html">YAML Loader</a>
     * @return a comparator to sort codes by their occurrence order
     */
    public Comparator<String> comparator(String field) {
        return Comparator.<String, Integer>comparing(code -> fetchFieldValue(code, field).asInt(100))
                         .thenComparing(this::normalizeCodeValue);
    }

    /**
     * Normalizes the given code by using the given mapping.
     * <p>
     * This first attempts to resolve the code by searching for the given mapping. If this doesn't yield a match,
     * {@link #normalize(String)} is attempted.
     *
     * @param code    the code to normalize / resolve
     * @param mapping the mapping to use as a first step when normalizing
     * @return the normalized code, or an empty optional if the given code is unknown
     */
    public Optional<String> normalizeWithMapping(String code, String mapping) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        String normalizedCodeValue = normalizeCodeValue(code);
        Optional<String> result = performNormalizeWithMapping(normalizedCodeValue, mappingsField + "." + mapping);
        if (result.isPresent()) {
            return result;
        }

        return performNormalize(normalizedCodeValue);
    }

    protected abstract Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping);

    /**
     * Attempts to normalize the given code using the given mapping or returns the input itself.
     *
     * @param code    the code to normalize
     * @param mapping the mapping to use as a first step when normalizing
     * @return the normalized code, or the original input which has been adjusted using
     * {@link #normalizeCodeValue(String)}
     * @see #normalizeWithMapping(String, String)
     */
    public String forcedNormalizeWithMapping(String code, String mapping) {
        return normalizeWithMapping(code, mapping).orElseGet(() -> normalizeCodeValue(code));
    }

    /**
     * Normalizes the given code by using the given mappings.
     * <p>
     * This first attempts to resolve the code by searching for the given mappings. If this doesn't yield a match,
     * {@link #normalize(String)} is attempted.
     * <p>
     * If a value for the standard <tt>acme 1.0</tt> is given, this would be invoked with <tt>acme-10</tt> as
     * primary mapping and <tt>acme</tt> as secondary. Therefore, if a mapping is present for the specific version,
     * this will be used. Otherwise, if a general value is present for <tt>acme</tt> this will be the result.
     * Finally, if all this fails, a common {@link #normalize(String)} is attempted for the given code.
     *
     * @param code             the code to normalize / resolve
     * @param primaryMapping   the more specific mapping used when resolving the code
     * @param secondaryMapping the more general mapping used when resolving the code
     * @return the normalized code, or an empty optional if the given code is unknown
     */
    public Optional<String> normalizeWithMappings(String code, String primaryMapping, String secondaryMapping) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        String normalizedCodeValue = normalizeCodeValue(code);
        Optional<String> result =
                performNormalizeWithMapping(normalizedCodeValue, mappingsField + "." + primaryMapping);
        if (result.isPresent()) {
            return result;
        }
        result = performNormalizeWithMapping(normalizedCodeValue, mappingsField + "." + secondaryMapping);
        if (result.isPresent()) {
            return result;
        }

        return performNormalize(normalizedCodeValue);
    }

    /**
     * Attempts to normalize the given code using the given mappings or returns the input itself.
     *
     * @param code             the code to normalize
     * @param primaryMapping   the more specific mapping used when resolving the code
     * @param secondaryMapping the more general mapping used when resolving the code
     * @return the normalized code or the original input which has been adjusted using
     * {@link #normalizeCodeValue(String)}
     * @see #normalizeWithMappings(String, String, String)
     */
    public String forcedNormalizeWithMappings(String code, String primaryMapping, String secondaryMapping) {
        return normalizeWithMappings(code, primaryMapping, secondaryMapping).orElseGet(() -> normalizeCodeValue(code));
    }

    /**
     * Performs a reverse lookup which transforms a given name into the leading code of this table.
     *
     * @param name the name (in any language) to be translated into a code
     * @return the leading code for the given name or an empty optional if the name is unknown
     */
    public Optional<String> reverseLookup(String name) {
        if (Strings.isEmpty(name)) {
            return Optional.empty();
        }

        return performReverseLookup(name);
    }

    protected abstract Optional<String> performReverseLookup(String name);

    /**
     * Normalizes user input (most commonly import data).
     * <p>
     * This actually just combines {@link #normalize(String)} with fallback to {@link #resolveName(String)} in
     * case a used re-imports data which has previously been exported using the name instead of the actual code.
     *
     * @param codeOrName the code or name to resolve into a leading code
     * @return the leading code for the given code or name, or an empty optional if the value is unknown
     */
    public Optional<String> normalizeInput(String codeOrName) {
        Optional<String> normalizedCode = normalize(codeOrName);
        if (normalizedCode.isPresent()) {
            return normalizedCode;
        }

        return reverseLookup(codeOrName);
    }

    /**
     * Fetches the given object based on the data in the lookup table.
     * <p>
     * The system will try to use a cache if possible.
     *
     * @param type the type of value to instantiate (Must accept a jackson ObjectNode in its constructor)
     * @param code the leading code used to determine which value to load
     * @param <T>  the generic type of the object to fetch
     * @return the object for the given code, or an empty optional if the code is unknown
     * @see #fetchObjectDirect(Class, String)
     */
    public <T> Optional<T> fetchObject(Class<T> type, String code) {
        if (Strings.isEmpty(type)) {
            return Optional.empty();
        }

        return performFetchObject(type, code, true);
    }

    /**
     * Fetches the given object based on the data in the lookup table - without using any cache.
     *
     * @param type the type of value to instantiate (Must accept a jackson ObjectNode in its constructor)
     * @param code the leading code used to determine which value to load
     * @param <T>  the generic type of the object to fetch
     * @return the object for the given code, or an empty optional if the code is unknown
     * @see #fetchObject(Class, String)
     */
    public <T> Optional<T> fetchObjectDirect(Class<T> type, String code) {
        if (Strings.isEmpty(type)) {
            return Optional.empty();
        }

        return performFetchObject(type, code, false);
    }

    protected abstract <T> Optional<T> performFetchObject(Class<T> type, @Nonnull String code, boolean useCache);

    /**
     * Provides a helper method to extract a translation table as used by Jupiter.
     * <p>
     * When querying a whole record a JSON using {@link #fetchObject(Class, String)}, we have to handle these
     * tables manually. Therefore, this method is used to parse them and {@link #fetchTranslation(Map, String)}
     * can be used to fetch the actual value.
     *
     * @param root the JSON object to query
     * @param path the path to the field to query
     * @return the parsed translation map. Note that this also gracefully handles simple string values
     */
    public static Map<String, String> parseTranslationTable(ObjectNode root, JsonPointer path) {
        return Json.tryGetAt(root, path).map(translations -> {
            if (translations.isObject()) {
                return translations.properties()
                                   .stream()
                                   .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().asText()));
            } else {
                return Collections.singletonMap(FALLBACK_LANGUAGE_CODE, translations.asText());
            }
        }).orElseGet(() -> {
            return Collections.singletonMap(FALLBACK_LANGUAGE_CODE, "");
        });
    }

    /**
     * Resolves the translation for a given language using a translation table.
     * <p>
     * This table can be built / parsed using {@link #parseTranslationTable(ObjectNode, JsonPointer)}.
     *
     * @param table    the table used to look up the value
     * @param language the language to translate to or <tt>null</tt> to use the current language
     * @return the translation for the given language, or a fallback value, or an empty optional if no translation is
     * present
     */
    public static Optional<String> fetchTranslation(Map<String, String> table, @Nullable String language) {
        String result = table.get(Strings.isFilled(language) ? language : NLS.getCurrentLanguage());
        if (Strings.isFilled(result)) {
            return Optional.of(result);
        }

        return Optional.ofNullable(table.get(FALLBACK_LANGUAGE_CODE));
    }

    /**
     * Parses and extracts an inner list of strings from a JSON object.
     * <p>
     * This can be used when processing JSON in order to build an object for {@link #fetchObject(Class, String)}.
     *
     * @param root the JSON object to query
     * @param path the path to the field to query
     * @return the string list found in the JSON, or an empty list if there is none
     */
    public static List<String> parseStringList(ObjectNode root, JsonPointer path) {
        Optional<JsonNode> optionalJsonNode = Json.tryGetAt(root, path);
        if (optionalJsonNode.isEmpty()) {
            return Collections.emptyList();
        }
        JsonNode jsonNode = optionalJsonNode.get();
        if (jsonNode.isArray()) {
            return transformArrayToStringList((ArrayNode) jsonNode);
        } else if (jsonNode.isTextual() && Strings.isFilled(jsonNode.asText())) {
            return Collections.singletonList(jsonNode.asText(null));
        } else {
            return Collections.emptyList();
        }
    }

    private static List<String> transformArrayToStringList(ArrayNode array) {
        return array.valueStream().map(JsonNode::asText).filter(Strings::isFilled).toList();
    }

    /**
     * Parses and extracts an inner map (of strings to string) from a JSON object.
     * <p>
     * This can be used when processing JSON in order to build an object for {@link #fetchObject(Class, String)}.
     *
     * @param root the JSON object to query
     * @param path the path to the field to query
     * @return the string map found in the JSON, or an empty map if there is none
     */
    public static Map<String, String> parseStringMap(ObjectNode root, JsonPointer path) {
        Optional<JsonNode> optionalJsonNode = Json.tryGetAt(root, path);
        if (optionalJsonNode.isEmpty()) {
            return Collections.emptyMap();
        }
        JsonNode jsonNode = optionalJsonNode.get();
        if (jsonNode.isObject()) {
            return jsonNode.properties()
                           .stream()
                           .filter(entry -> Strings.isFilled(entry.getValue().asText()))
                           .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().asText()));
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Parses and extracts an inner map (of strings to a list of strings) from a JSON object.
     * <p>
     * This can be used when processing JSON in order to build an object for {@link #fetchObject(Class, String)}.
     *
     * @param root the JSON object to query
     * @param path the path to the field to query
     * @return the map found in the JSON, or an empty map if there is none
     */
    public static Map<String, List<String>> parseStringListMap(ObjectNode root, JsonPointer path) {
        Optional<JsonNode> optionalJsonNode = Json.tryGetAt(root, path);
        if (optionalJsonNode.isEmpty()) {
            return Collections.emptyMap();
        }
        JsonNode jsonNode = optionalJsonNode.get();
        if (jsonNode.isObject()) {
            return jsonNode.properties()
                           .stream()
                           .filter(entry -> entry.getValue().isArray())
                           .collect(Collectors.toMap(Map.Entry::getKey,
                                                     entry -> transformArrayToStringList((ArrayNode) entry.getValue())));
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Parses and extracts an inner map (of strings to jackson ObjectNodes) from a JSON object.
     * <p>
     * This can be used when processing JSON in order to build an object for {@link #fetchObject(Class, String)}.
     *
     * @param root the JSON object to query
     * @param path the path to the field to query
     * @return the map found in the JSON, or an empty map if there is none
     */
    public static Map<String, ObjectNode> parseMap(ObjectNode root, JsonPointer path) {
        Optional<JsonNode> optionalJsonNode = Json.tryGetAt(root, path);
        if (optionalJsonNode.isEmpty()) {
            return Collections.emptyMap();
        }
        JsonNode jsonNode = optionalJsonNode.get();
        if (jsonNode.isObject()) {
            return jsonNode.properties()
                           .stream()
                           .filter(entry -> entry.getValue().isObject())
                           .collect(Collectors.toMap(Map.Entry::getKey, entry -> (ObjectNode) entry.getValue()));
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Suggests several entries for the given search term aside from deprecated entries using the currently
     * active language.
     *
     * @param searchTerm the term used to filter the suggestions
     * @return a stream of suggestions for the given term. Note that most probably {@link Stream#limit(long)} should
     * be used on the result as this might yield quite a bunch of suggestions in order to optimize internal queries.
     */
    public Stream<LookupTableEntry> suggest(@Nullable String searchTerm) {
        return suggest(searchTerm, NLS.getCurrentLanguage(), false);
    }

    /**
     * Suggests several entries for the given search term aside from deprecated entries using the given language.
     *
     * @param searchTerm the term used to filter the suggestions
     * @param language   the language to translate the name and description to
     * @return a stream of suggestions for the given term. Note that most probably {@link Stream#limit(long)} should
     * be used on the result as this might yield quite a bunch of suggestions in order to optimize internal queries.
     */
    public Stream<LookupTableEntry> suggest(@Nullable String searchTerm, String language) {
        if (Strings.isEmpty(searchTerm)) {
            return scan(language, new Limit(0, MAX_SUGGESTIONS), false);
        }
        return performSuggest(new Limit(0, MAX_SUGGESTIONS), searchTerm, language, false);
    }

    /**
     * Suggests several entries for the given search term using the given language.
     *
     * @param searchTerm               the term used to filter the suggestions
     * @param language                 the language to translate the name and description to
     * @param considerDeprecatedValues whether deprecated values should be suggested as well
     * @return a stream of suggestions for the given term. Note that most probably {@link Stream#limit(long)} should
     * be used on the result as this might yield quite a bunch of suggestions in order to optimize internal queries.
     */
    public Stream<LookupTableEntry> suggest(@Nullable String searchTerm,
                                            String language,
                                            boolean considerDeprecatedValues) {
        if (Strings.isEmpty(searchTerm)) {
            return scan(language, new Limit(0, MAX_SUGGESTIONS), considerDeprecatedValues);
        }
        return performSuggest(new Limit(0, MAX_SUGGESTIONS), searchTerm, language, considerDeprecatedValues);
    }

    /**
     * Computes the actual suggestions for the given query and limit.
     *
     * @param limit                    regulates how many suggestions are generated
     * @param searchTerm               the term to search by
     * @param language                 the language to translate names and descriptions in
     * @param considerDeprecatedValues whether deprecated values should be suggested as well
     * @return a stream containing all suggestions
     */
    protected abstract Stream<LookupTableEntry> performSuggest(Limit limit,
                                                               String searchTerm,
                                                               String language,
                                                               boolean considerDeprecatedValues);

    /**
     * Enumerates all entries in the table aside from deprecated entries using the current language.
     *
     * @param limit the limit to apply to fetch a sane number of entries
     * @return a stream of all entries in this table, or an empty stream is scanning isn't supported
     */
    public Stream<LookupTableEntry> scan(Limit limit) {
        return scan(NLS.getCurrentLanguage(), limit, true);
    }

    /**
     * Enumerates all entries in the table aside from deprecated entries using the given language.
     *
     * @param language the language to translate the name and description to
     * @param limit    the limit to apply to fetch a sane number of entries
     * @return a stream of the selected amount of entries in this table
     */
    public Stream<LookupTableEntry> scan(String language, Limit limit) {
        return scan(language, limit, true);
    }

    /**
     * Enumerates all entries in the table using the given language.
     *
     * @param language                 the language to translate the name and description to
     * @param limit                    the limit to apply to fetch a sane number of entries
     * @param considerDeprecatedValues whether deprecated values should be included in the scan
     * @return a stream of the selected amount of entries in this table
     */
    public abstract Stream<LookupTableEntry> scan(String language, Limit limit, boolean considerDeprecatedValues);

    /**
     * Executes a search for the given (optional) search term.
     * <p>
     * In contrast to {@link #suggest(String, String, boolean)}, this provides support for pagination and will always
     * contain {@link LookupTableEntry#isDeprecated() deprecated} entries. Also, {@link LookupTableEntry#getSource()}
     * will be populated.
     *
     * @param searchTerm the term used to filter the suggestions
     * @param limit      the limit to apply to fetch a sane number of entries
     * @return a stream of matches for the given term
     */
    public Stream<LookupTableEntry> search(@Nullable String searchTerm, Limit limit) {
        return search(searchTerm, limit, NLS.getCurrentLanguage());
    }

    /**
     * Executes a search for the given (optional) search term.
     * <p>
     * In contrast to {@link #suggest(String, String, boolean)}, this provides support for pagination and will also contain
     * {@link LookupTableEntry#isDeprecated() deprecated} entries. Also, {@link LookupTableEntry#getSource()}
     * will be populated.
     *
     * @param searchTerm the term used to filter the suggestions
     * @param limit      the limit to apply to fetch a sane number of entries
     * @param language   the language to translate the name and description to
     * @return a stream of matches for the given term
     */
    public Stream<LookupTableEntry> search(@Nullable String searchTerm, Limit limit, String language) {
        if (Strings.isEmpty(searchTerm)) {
            return scan(language, limit, true);
        } else {
            return performSearch(searchTerm, limit, language);
        }
    }

    protected abstract Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language);

    /**
     * Returns the number of entries in this table.
     *
     * @return the number of entries in this table
     */
    public abstract int count();

    /**
     * Enumerates all entries matching the given lookup in the table using the given language.
     *
     * @param language    the language to translate the name and description to
     * @param lookupPath  the field to search in
     * @param lookupValue the value to search for
     * @return a stream of all entries matching the lookup, or an empty stream if scanning isn't supported
     */
    public Stream<LookupTableEntry> query(String language, String lookupPath, String lookupValue) {
        return performQuery(language, lookupPath, lookupValue);
    }

    protected abstract Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue);

    public String getTitle() {
        return Optional.of(extension.getTranslatedString("title"))
                       .filter(Strings::isFilled)
                       .or(() -> NLS.getIfExists("LookupTable." + extension.getId(), null))
                       .orElse(extension.getId());
    }

    public String getDescription() {
        return Optional.of(extension.getTranslatedString("description"))
                       .filter(Strings::isFilled)
                       .or(() -> NLS.getIfExists("LookupTable." + extension.getId() + ".description", null))
                       .orElse("");
    }
}
