/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides an implementation for {@link LookupTable} used for testing purposes.
 * <p>
 * Values are provided in the test.conf following this schema:
 */
class TestJsonLookupTable extends LookupTable {

    public static final String CONFIG_KEY_TEST_DATA_JSON = "testDataJson";

    private final ObjectNode json;

    TestJsonLookupTable(Extension extension) {
        super(extension);
        if (!Sirius.isTest()) {
            throw new IllegalStateException(getClass().getSimpleName() + " only supported while in test mode.");
        }
        this.json = Json.parseObject(extension.get(CONFIG_KEY_TEST_DATA_JSON).asString());
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return json.has(code);
    }

    @Override
    protected Optional<String> performResolveName(String code, String language) {
        return Optional.ofNullable(json.path(code).path("name").path(language).asText());
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String language) {
        return Optional.ofNullable(json.path(code).path("description").path(language).asText());
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        return performFetchTranslatedField(code, targetField, NLS.getCurrentLanguage()).map(Value::of)
                                                                                       .orElse(Value.EMPTY);
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String language) {
        return Optional.ofNullable(json.path(code).path(targetField).path(language).asText());
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        if (json.has(code)) {
            return Optional.of(code);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping) {
        // Not supported yet
        return Optional.empty();
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        // Not supported yet
        return Optional.empty();
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        // Not supported yet
        return Optional.empty();
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String language) {
        return Streams.stream(json.fields())
                      .filter(entry -> filter(entry, searchTerm, language))
                      .map(entry -> extractEntryData(entry.getKey(), entry.getValue(), language))
                      .skip(limit.getItemsToSkip())
                      .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    private boolean filter(Map.Entry<String, JsonNode> entry, String searchTerm, String language) {
        if (Strings.isEmpty(searchTerm)) {
            return true;
        }

        String effectiveSearchTerm = searchTerm.toLowerCase();
        return Value.of(entry.getKey()).toLowerCase().contains(effectiveSearchTerm)
               || Value.of(entry.getValue()
                                .path("name."
                                      + language)
                                .asText())
                       .toLowerCase()
                       .contains(effectiveSearchTerm)
               || Value.of(entry.getValue().path("description." + language).asText())
                       .toLowerCase()
                       .contains(effectiveSearchTerm);
    }

    @Override
    protected Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language) {
        // Deprecations or source data not supported yet, so we can re-use the same method..
        return performSuggest(limit, searchTerm, language);
    }

    private LookupTableEntry extractEntryData(String code, JsonNode entry, String language) {
        return new LookupTableEntry(code,
                                    entry.path("name").path(language).asText(),
                                    entry.path("description").path(language).asText());
    }

    @Override
    public Stream<LookupTableEntry> scan(String language, Limit limit) {
        return Streams.stream(json.fields())
                      .filter(entry -> entry.getKey().equals(language))
                      .skip(limit.getItemsToSkip())
                      .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                      .map(entry -> extractEntryData(entry.getKey(), entry.getValue(), language));
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue) {
        // Not supported yet
        return Stream.empty();
    }

    @Override
    public int count() {
        return (int) Streams.stream(json.fieldNames()).count();
    }
}
