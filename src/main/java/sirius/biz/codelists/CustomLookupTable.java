/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Limit;
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
        super(extension);
        this.customTable = customTable;
        this.baseTable = baseTable;
    }

    private static <T> Optional<T> or(Optional<T> main, Supplier<Optional<T>> alternative) {
        if (main.isPresent()) {
            return main;
        }

        return alternative.get();
    }

    @Override
    protected Optional<String> performResolveName(String code, String lang) {
        return or(customTable.performResolveName(code, lang), () -> baseTable.performResolveName(code, lang));
    }

    @Override
    protected Optional<String> performFetchField(String code, String targetField) {
        return or(customTable.performFetchField(code, targetField),
                  () -> baseTable.performFetchField(code, targetField));
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
        return Stream.concat(customTable.performSuggest(limit, searchTerm, lang),
                             baseTable.performSuggest(limit, searchTerm, lang));
    }

    @Override
    protected Stream<LookupTableEntry> performScan(String lang) {
        return Stream.concat(customTable.performScan(lang), baseTable.performScan(lang));
    }
}
