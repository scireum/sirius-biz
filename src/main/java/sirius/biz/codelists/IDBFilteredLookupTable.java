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
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Uses a {@link LookupTable lookup table} and filters its entries using a given {@link IDBSet}.
 */
class IDBFilteredLookupTable extends LookupTable {

    private final LookupTable baseTable;
    private final IDBSet filterSet;

    @Part
    @Nullable
    private static Jupiter jupiter;

    IDBFilteredLookupTable(Extension extension, LookupTable baseTable, IDBSet filterSet) {
        super(extension);
        this.baseTable = baseTable;
        this.filterSet = filterSet;
        this.codeCase = extension.get(LookupTable.CONFIG_KEY_CODE_CASE_MODE)
                                 .upperCase()
                                 .getEnum(CodeCase.class)
                                 .orElse(baseTable.codeCase);
    }

    protected boolean contains(String code) {
        return jupiter.fetchFromSmallCache("set-contains-" + filterSet.getName() + "-" + code,
                                           () -> filterSet.contains(code));
    }

    @Override
    protected Optional<String> performResolveName(String code, String lang) {
        if (contains(code)) {
            return baseTable.performResolveName(code, lang);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String lang) {
        if (contains(code)) {
            return baseTable.performResolveDescription(code, lang);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        if (contains(code)) {
            return baseTable.performFetchField(code, targetField);
        } else {
            return Value.EMPTY;
        }
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String lang) {
        if (contains(code)) {
            return baseTable.performFetchTranslatedField(code, targetField, lang);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> performNormalize(String code) {
        return baseTable.performNormalize(code).filter(this::contains);
    }

    @Override
    protected Optional<String> performNormalizeWithMapping(@Nonnull String code, String mapping) {
        return baseTable.performNormalizeWithMapping(code, mapping).filter(this::contains);
    }

    @Override
    protected Optional<String> performReverseLookup(String name) {
        return baseTable.performReverseLookup(name).filter(this::contains);
    }

    @Override
    protected <T> Optional<T> performFetchObject(Class<T> type, String code, boolean useCache) {
        if (contains(code)) {
            return baseTable.performFetchObject(type, code, useCache);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String lang) {
        return baseTable.performSuggest(limit, searchTerm, lang).filter(pair -> contains(pair.getCode()));
    }

    @Override
    protected Stream<LookupTableEntry> performScan(String lang) {
        return baseTable.scan(lang).filter(pair -> contains(pair.getCode()));
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String lang, String lookupPath, String lookupValue) {
        return baseTable.query(lang, lookupPath, lookupValue).filter(pair -> contains(pair.getCode()));
    }
}
