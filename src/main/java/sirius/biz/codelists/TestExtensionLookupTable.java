/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.Sirius;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides an implementation for {@link LookupTable} based on {@link Extension} for testing purposes.
 */
class TestExtensionLookupTable extends LookupTable {

    TestExtensionLookupTable(Extension extension) {
        super(extension);
        if (!Sirius.isTest()) {
            throw new IllegalStateException(getClass().getSimpleName() + " only supported while in test mode.");
        }
    }

    @Override
    protected boolean performContains(@Nonnull String code) {
        return extension.has(code);
    }

    @Override
    protected Optional<String> performResolveName(String code, String language) {
        return Optional.ofNullable(extension.getTranslatedString(code + ".name", language));
    }

    @Override
    protected Optional<String> performResolveDescription(@Nonnull String code, String language) {
        return Optional.ofNullable(extension.getTranslatedString(code + ".description", language));
    }

    @Override
    protected Value performFetchField(String code, String targetField) {
        return performFetchTranslatedField(code, targetField, NLS.getCurrentLanguage()).map(Value::of)
                                                                                       .orElse(Value.EMPTY);
    }

    @Override
    protected Optional<String> performFetchTranslatedField(String code, String targetField, String language) {
        return Optional.ofNullable(extension.getTranslatedString(code + "." + targetField, language));
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

    @Override
    protected Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String language) {
        // Not supported yet
        return Stream.empty();
    }

    @Override
    protected Stream<LookupTableEntry> performSearch(String searchTerm, Limit limit, String language) {
        // Deprecations or source data not supported yet, so we can re-use the same method..
        return performSuggest(limit, searchTerm, language);
    }

    @Override
    public Stream<LookupTableEntry> scan(String language, Limit limit) {
        Context tableContext = extension.getContext();
        return tableContext.keySet()
                           .stream()
                           .skip(limit.getItemsToSkip())
                           .limit(limit.getMaxItems() == 0 ? Long.MAX_VALUE : limit.getMaxItems())
                           .map(key -> new LookupTableEntry(key,
                                                            resolveName(key, language).orElse(key),
                                                            resolveDescription(key, language).orElse("")));
    }

    @Override
    protected Stream<LookupTableEntry> performQuery(String language, String lookupPath, String lookupValue) {
        // Not supported yet
        return Stream.empty();
    }

    @Override
    public int count() {
        return extension.getConfigList(extension.getId()).size();
    }
}
