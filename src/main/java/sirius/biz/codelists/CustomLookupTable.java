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
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Combines two {@link LookupTable lookup tables} into a single lookup table.
 * <p>
 * Using this approach, the custom table is always queried first and then complemented by the base table.
 */
class CustomLookupTable extends LookupTable {

    private final LookupTable customTable;
    private final LookupTable baseTable;

    CustomLookupTable(Extension extension, LookupTable customTable, LookupTable baseTable) {
        super(extension, baseTable.codeCase);
        this.customTable = customTable;
        this.baseTable = baseTable;
    }

    private static <T> Optional<T> or(Optional<T> main, Supplier<Optional<T>> alternative) {
        if (main.isPresent()) {
            return main;
        }

        return alternative.get();
    @Override
    protected boolean performContains(@Nonnull String code) {
        return customTable.performContains(code) || baseTable.performContains(code);
    }

    @Override
    protected Optional<String> performResolveName(String code, String lang) {
        return or(customTable.performResolveName(code, lang), () -> baseTable.performResolveName(code, lang));
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String lang) {
        return or(customTable.performResolveDescription(code, lang),
                  () -> baseTable.performResolveDescription(code, lang));
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
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String lang) {
        return or(customTable.performFetchTranslatedField(code, targetField, lang),
                  () -> baseTable.performFetchTranslatedField(code, targetField, lang));
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        return or(customTable.performNormalize(code), () -> baseTable.performNormalize(code));
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping) {
        return or(customTable.performNormalizeWithMapping(code, mapping),
                  () -> baseTable.performNormalizeWithMapping(code, mapping));
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        return or(customTable.performReverseLookup(name), () -> baseTable.performReverseLookup(name));
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        return or(customTable.performFetchObject(type, code, useCache),
                  () -> baseTable.performFetchObject(type, code, useCache));
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String lang) {
        return Stream.concat(customTable.performSuggest(Limit.UNLIMITED, searchTerm, lang),
                             baseTable.performSuggest(Limit.UNLIMITED, searchTerm, lang))
                     .skip(limit.getItemsToSkip())
                     .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    @Override
    public Stream<LookupTableEntry> scan(String lang, Limit limit) {
        return Stream.concat(customTable.scan(lang, Limit.UNLIMITED), baseTable.scan(lang, Limit.UNLIMITED))
                     .skip(limit.getItemsToSkip())
                     .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    @Override
    public int count() {
        return customTable.count() + baseTable.count();
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String lang, String lookupPath, String lookupValue) {
        return Stream.concat(customTable.performQuery(lang, lookupPath, lookupValue),
                             baseTable.performQuery(lang, lookupPath, lookupValue));
    }
}
