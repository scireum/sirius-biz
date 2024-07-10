/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides an implementation for {@link LookupTable} based on {@link Extension config data}.
 */
class ConfigLookupTable extends LookupTable {

    private static final String CONFIG_KEY_DATA = "data";

    ConfigLookupTable(Extension extension) {
        super(extension);
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return extension.has(CONFIG_KEY_DATA + "." + code);
    }

    @Override
    protected Optional<String> performResolveName(String code, String language) {
        return Optional.ofNullable(extension.getTranslatedString(Strings.apply("%s.%s.name", CONFIG_KEY_DATA, code),
                                                                 language));
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String language) {
        return Optional.ofNullable(extension.getTranslatedString(Strings.apply("%s.%s.description",
                                                                               CONFIG_KEY_DATA,
                                                                               code), language));
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        return performFetchTranslatedField(code, targetField, NLS.getCurrentLanguage()).map(Value::of)
                                                                                       .orElse(Value.EMPTY);
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String language) {
        return Optional.ofNullable(extension.getTranslatedString(Strings.apply("%s.%s.%s",
                                                                               CONFIG_KEY_DATA,
                                                                               code,
                                                                               targetField), language));
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        if (performContains(code)) {
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

    @SuppressWarnings("unchecked")
    @Explain("The data is provided in the configuration as String keys with assigned value objects.")
    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit,
                                                      String searchTerm,
                                                      String language,
                                                      boolean considerDeprecatedValues) {
        Map<String, Object> data = extension.get(CONFIG_KEY_DATA).get(Map.class, Collections.emptyMap());
        return data.keySet()
                   .stream()
                   .filter(key -> filter(key, searchTerm, language))
                   .map(key -> extractEntryData(key, language))
                   .skip(limit.getItemsToSkip())
                   .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    private LookupTableEntry extractEntryData(String code, String language) {
        return new LookupTableEntry(code,
                                    resolveName(code, language).orElse(code),
                                    resolveDescription(code, language).orElse(""));
    }

    private boolean filter(String code, String searchTerm, String language) {
        if (Strings.isEmpty(searchTerm)) {
            return true;
        }

        String effectiveSearchTerm = searchTerm.toLowerCase();
        return Value.of(code).toLowerCase().contains(effectiveSearchTerm) || Value.of(resolveName(code, language))
                                                                                  .toLowerCase()
                                                                                  .contains(effectiveSearchTerm);
    }

    @Override
    protected Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language) {
        // Deprecations or source data not supported yet, so we can re-use the same method..
        return performSuggest(limit, searchTerm, language, true);
    }

    @SuppressWarnings("unchecked")
    @Explain("The data is provided in the configuration as String keys with assigned value objects.")
    @Override
    public Stream<LookupTableEntry> scan(String language, Limit limit, boolean considerDeprecatedValues) {
        Map<String, Object> data = extension.get(CONFIG_KEY_DATA).get(Map.class, Collections.emptyMap());
        return data.keySet()
                   .stream()
                   .skip(limit.getItemsToSkip())
                   .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                   .map(key -> extractEntryData(key, language));
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue) {
        // Not supported yet
        return Stream.empty();
    }

    @Override
    public int count() {
        return extension.get(CONFIG_KEY_DATA).get(Map.class, Collections.emptyMap()).size();
    }
}
