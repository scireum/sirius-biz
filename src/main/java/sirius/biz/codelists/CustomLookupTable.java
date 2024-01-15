/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Value;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Combines two {@link LookupTable lookup tables} into a single lookup table.
 * <p>
 * Using this approach, the custom table is always queried first and then complemented by the base table.
 * If there is a custom entry with the same key as in the base table, the custom entry is used.
 */
class CustomLookupTable extends LookupTable {

    private final LookupTable customTable;
    private final LookupTable baseTable;

    CustomLookupTable(Extension extension, LookupTable customTable, LookupTable baseTable) {
        super(extension, baseTable.codeCase);
        this.customTable = customTable;
        this.baseTable = baseTable;
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return customTable.performContains(code) || baseTable.performContains(code);
    }

    @Override
    protected Optional<String> performResolveName(String code, String language) {
        return customTable.performResolveName(code, language).or(() -> baseTable.performResolveName(code, language));
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String language) {
        return customTable.performResolveDescription(code, language)
                          .or(() -> baseTable.performResolveDescription(code, language));
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        Value result = customTable.performFetchField(code, targetField);
        if (result.isFilled()) {
            return result;
        }
        return baseTable.performFetchField(code, targetField);
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String language) {
        return customTable.performFetchTranslatedField(code, targetField, language)
                          .or(() -> baseTable.performFetchTranslatedField(code, targetField, language));
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        return customTable.performNormalize(code).or(() -> baseTable.performNormalize(code));
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping) {
        return customTable.performNormalizeWithMapping(code, mapping)
                          .or(() -> baseTable.performNormalizeWithMapping(code, mapping));
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        return customTable.performReverseLookup(name).or(() -> baseTable.performReverseLookup(name));
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        return customTable.performFetchObject(type, code, useCache)
                          .or(() -> baseTable.performFetchObject(type, code, useCache));
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String language) {
        return Stream.concat(customTable.performSuggest(Limit.UNLIMITED, searchTerm, language),
                             baseTable.performSuggest(Limit.UNLIMITED, searchTerm, language))
                     .skip(limit.getItemsToSkip())
                     .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                     .collect(filterCustomDuplicateCollector())
                     .values()
                     .stream();
    }

    @Override
    protected Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language) {
        return Stream.concat(customTable.performSearch(searchTerm, Limit.UNLIMITED, language),
                             baseTable.performSearch(searchTerm, Limit.UNLIMITED, language))
                     .skip(limit.getItemsToSkip())
                     .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                     .collect(Collectors.toMap(LookupTableEntry::getCode, Function.identity(), (a, b) -> a))
                     .values()
                     .stream();
    }

    @Override
    public Stream<LookupTableEntry> scan(String language, Limit limit) {
        return Stream.concat(customTable.scan(language, Limit.UNLIMITED), baseTable.scan(language, Limit.UNLIMITED))
                     .skip(limit.getItemsToSkip())
                     .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                     .collect(filterCustomDuplicateCollector())
                     .values()
                     .stream();
    }

    @Override
    public int count() {
        return customTable.count() + baseTable.count();
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue) {
        return Stream.concat(customTable.performQuery(language, lookupPath, lookupValue),
                             baseTable.performQuery(language, lookupPath, lookupValue))
                     .collect(filterCustomDuplicateCollector())
                     .values()
                     .stream();
    }

    private Collector<LookupTableEntry, ?, Map<String, LookupTableEntry>> filterCustomDuplicateCollector() {
        return Collectors.toMap(LookupTableEntry::getCode, Function.identity(), (a, b) -> a);
    }
}
