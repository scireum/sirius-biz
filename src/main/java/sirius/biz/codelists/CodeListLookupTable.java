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
import sirius.kernel.settings.Extension;

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
    protected Optional<String> performResolveName(String code, String lang) {
        return Optional.ofNullable(codeLists.getTranslatedValue(codeList, code, lang));
    }

    @Override
    protected Optional<String> performFetchField(String code, String targetField) {
        return Optional.ofNullable(codeLists.getValues(codeList, code).getSecond());
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
        return scan().filter(pair -> Strings.equalIgnoreCase(name, pair.getName()))
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
                        .map(entry -> extractEntryData(entry, lang));
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
    protected Stream<LookupTableEntry> performScan(String lang) {
        return codeLists.getEntries(codeList).stream().map(entry -> extractEntryData(entry, lang));
    }
}
