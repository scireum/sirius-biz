/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.jupiter.IDBSet;
import sirius.biz.jupiter.Jupiter;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Uses a {@link LookupTable lookup table} and filters its entries using a given {@link IDBSet}.
 */
class IDBFilteredLookupTable extends LookupTable {

    private static final String CACHE_PREFIX_CARDINALITY = "cardinality-";

    private final LookupTable baseTable;
    private final IDBSet filterSet;

    @Part
    @Nullable
    private static Jupiter jupiter;

    IDBFilteredLookupTable(Extension extension, LookupTable baseTable, IDBSet filterSet) {
        super(extension, baseTable.codeCase);
        this.baseTable = baseTable;
        this.filterSet = filterSet;
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return jupiter.fetchFromSmallCache("set-contains-" + filterSet.getName() + "-" + code,
                                           () -> filterSet.contains(code));
    }

    @Override
    protected Optional<String> performResolveName(String code, String language) {
        if (performContains(code)) {
            return baseTable.performResolveName(code, language);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String language) {
        if (performContains(code)) {
            return baseTable.performResolveDescription(code, language);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        if (performContains(code)) {
            return baseTable.performFetchField(code, targetField);
        } else {
            return Value.EMPTY;
        }
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String language) {
        if (performContains(code)) {
            return baseTable.performFetchTranslatedField(code, targetField, language);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        return baseTable.performNormalize(code).filter(this::performContains);
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping) {
        return baseTable.performNormalizeWithMapping(code, mapping).filter(this::performContains);
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        return baseTable.performReverseLookup(name).filter(this::performContains);
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        if (performContains(code)) {
            return baseTable.performFetchObject(type, code, useCache);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String language) {
        return baseTable.performSuggest(Limit.UNLIMITED, searchTerm, language)
                        .filter(pair -> performContains(pair.getCode()))
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    @Override
    public Stream<LookupTableEntry> scan(String language, Limit limit) {
        return baseTable.scan(language, Limit.UNLIMITED)
                        .filter(pair -> performContains(pair.getCode()))
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    @Override
    protected Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language) {
        return baseTable.performSearch(searchTerm, Limit.UNLIMITED, language)
                        .filter(pair -> performContains(pair.getCode()))
                        .skip(limit.getItemsToSkip())
                        .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems());
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue) {
        return baseTable.query(language, lookupPath, lookupValue).filter(pair -> performContains(pair.getCode()));
    }

    @Override
    public int count() {
        try {
            return jupiter.fetchFromSmallCache(CACHE_PREFIX_CARDINALITY + filterSet.getName(), filterSet::size);
        } catch (Exception exception) {
            Exceptions.createHandled()
                      .to(Jupiter.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Failed to fetch entry count of set '%s': %s (%s)", filterSet.getName())
                      .handle();
            return 0;
        }
    }
}
