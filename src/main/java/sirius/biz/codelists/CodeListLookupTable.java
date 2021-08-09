/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides an implementation for {@link LookupTable} based on {@link CodeLists}.
 */
class CodeListLookupTable extends LookupTable {

    @Part
    private static CodeLists<?, ?, ?> codeLists;

    private static final Cache<String, String> REVERSE_LOOKUP_CACHE =
            CacheManager.createCoherentCache("codelists-reverse-lookup");

    private final String codeList;

    CodeListLookupTable(Extension extension, String codeList) {
        super(extension);
        this.codeList = codeList;
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return codeLists.hasValue(codeList, code);
    }

    @Override
    protected Optional<String> performResolveName(String code, String lang) {
        return Optional.ofNullable(codeLists.getTranslatedValue(codeList, code, lang));
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String lang) {
        // Descriptions are not supported by code lists...
        return Optional.empty();
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        return Value.of(codeLists.getValues(codeList, code).getSecond());
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String lang) {
        return Optional.ofNullable(codeLists.getTranslatedValues(codeList, code, lang).getSecond());
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        if (codeLists.hasValue(codeList, code)) {
            return Optional.of(code);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping) {
        // This isn't supported by code lists...
        return Optional.empty();
    }

    /**
     * Flushes the internal cache which is used for reverse (value to code) lookups.
     */
    public static void flushReverseLookupCache() {
        REVERSE_LOOKUP_CACHE.clear();
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        return Optional.ofNullable(REVERSE_LOOKUP_CACHE.get(codeList + "-" + fetchCodeListTenantId() + "-" + name,
                                                            ignored -> performReverseLookupScan(name)));
    }

    private String fetchCodeListTenantId() {
        return codeLists.getRequiredTenant(codeList).getIdAsString();
    }

    private String performReverseLookupScan(String name) {
        return scan(NLS.getCurrentLang(), Limit.UNLIMITED).filter(pair -> Strings.equalIgnoreCase(name, pair.getName()))
                                                          .findFirst()
                                                          .map(LookupTableEntry::getCode)
                                                          .orElse(null);
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        return Optional.empty();
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String lang) {
        return codeLists.getEntries(codeList)
                        .stream()
                        .filter(entry -> filter(entry, searchTerm, lang))
                        .map(entry -> extractEntryData(entry, lang))
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    @Override
    protected Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String lang) {
        // As plain code lists don't support deprecations or source data, we can re-use the same
        // method..
        return performSuggest(limit, searchTerm, lang);
    }

    private LookupTableEntry extractEntryData(CodeListEntry<?, ?> entry, String lang) {
        return new LookupTableEntry(entry.getCodeListEntryData().getCode(),
                                    entry.getCodeListEntryData().getTranslatedValue(lang),
                                    entry.getCodeListEntryData().getDescription());
    }

    private boolean filter(CodeListEntry<?, ?> entry, String searchTerm, String lang) {
        if (Strings.isEmpty(searchTerm)) {
            return true;
        }

        String effectiveSearchTerm = searchTerm.toLowerCase();
        return Value.of(entry.getCodeListEntryData().getCode()).toLowerCase().contains(effectiveSearchTerm) || Value.of(
                entry.getCodeListEntryData().getTranslatedValue(lang)).toLowerCase().contains(effectiveSearchTerm);
    }

    @Override
    public Stream<LookupTableEntry> scan(String lang, Limit limit) {
        return codeLists.getEntries(codeList)
                        .stream()
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                        .map(entry -> extractEntryData(entry, lang));
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String lang, String lookupPath, String lookupValue) {
        // This would need a complex caching strategy as always fetching the DB would be too expensive.
        // Could be implemented when needed.
        throw new UnsupportedOperationException();
    }

    @Override
    public int count() {
        return codeLists.getEntries(codeList).size();
    }
}
