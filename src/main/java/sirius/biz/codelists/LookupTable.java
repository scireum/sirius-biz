/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides a table of master data which maps one or more code fields to one or more payload values.
 * <p>
 * Lookup tables are actually an abstraction over {@link CodeLists} and {@link sirius.biz.jupiter.IDBTable tables}
 * provided by Jupiter. Based on the system config, a lookup table can be represented by either of those.
 * <p>
 * A lookup table has a normalized code field, which is used e.g. in a database entity to represent a value. Using
 * {@link #normalize(String)} one can transform aliased codes (e.g. a three letter ISO language code) into the main
 * code (e.g. a two letter ISO code). This can also be used to verify that a code is valid.
 * <p>
 * Next to the code there is always a name field which is used to provide a textual representation of the field. This
 * can be automatically translated to the users language.
 * <p>
 * Using {@link #fetchField(String, String)}, {@link #fetchTranslatedField(String, String)} or even
 * {@link #fetchObject(Class, String)} additional values can be resolved (esp. when using Jupiter).
 * <p>
 * Referencing lookup tables is further simplified by {@link LookupValue} which can be directly embedded into
 * database entities and &lt;lookupField&gt; which can be used to render an autocomplete along with a value selection
 * helper for said fields.
 * <p>
 * To actually control how a lookup table is represented internally, a config section within <tt>lookup-tables</tt>
 * has to be defined. See <tt>component-biz.conf</tt> for further details. If no matching section is found, the
 * {@link CodeLists code list} with the same name is used.
 */
public abstract class LookupTable {

    private static final int MAX_SUGGESTIONS = 25;
    private static final String CONFIG_KEY_SUPPORTS_SCAN = "supportsScan";
    private static final String CONFIG_KEY_CODE_CASE_MODE = "codeCase";
    private final boolean supportsScan;
    private final CodeCase codeCase;

    enum CodeCase {
        LOWER, UPPER, VERBATIM
    }

    protected final Extension extension;

    protected LookupTable(Extension extension) {
        this.extension = extension;
        this.supportsScan = extension.get(CONFIG_KEY_SUPPORTS_SCAN).asBoolean();
        this.codeCase =
                extension.get(CONFIG_KEY_CODE_CASE_MODE).upperCase().getEnum(CodeCase.class).orElse(CodeCase.VERBATIM);
    }

    protected String normalizeCodeValue(String code) {
        switch (codeCase) {
            case LOWER:
                return code.toLowerCase();
            case UPPER:
                return code.toUpperCase();
            default:
                return code;
        }
    }

    /**
     * Resolves the name for the given code.
     *
     * @param code the code to resolve the name for
     * @return the name for the given code in the currently active language or an empty optional, if the code
     * is unknown. Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> resolveName(String code) {
        return resolveName(code, NLS.getCurrentLang());
    }

    /**
     * Resolves the name in the given language for the given code.
     *
     * @param code the code to resolve the name for
     * @param lang the language of the name to resolve
     * @return the name for the given code in the given language or an empty optional, if the code is unknown.
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> resolveName(String code, String lang) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performResolveName(normalizeCodeValue(code), lang);
    }

    protected abstract Optional<String> performResolveName(@Nonnull String code, String lang);

    /**
     * Fetches the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @return the value of the field or an empty optional if the code is unknown. Note that this will only resolve the
     * main code. When in doubt, the code must be normalized via {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> fetchField(String code, String targetField) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performFetchField(normalizeCodeValue(code), targetField);
    }

    protected abstract Optional<String> performFetchField(@Nonnull String code, String targetField);

    /**
     * Fetches the translated value of the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @return the translated value of the field using the current language or an empty optional if the code is unknown.
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> fetchTranslatedField(String code, String targetField) {
        return fetchTranslatedField(code, targetField, NLS.getCurrentLang());
    }

    /**
     * Fetches the translated value of the requested field for the given code.
     *
     * @param code        the code to fetch the field for
     * @param targetField the field to fetch
     * @param lang        the language used to translate the resulting value
     * @return the translated value of the field using the given language or an empty optional if the code is unknown.
     * Note that this will only resolve the main code. When in doubt, the code must be normalized via
     * {@link #normalize(String)} before invoking this method.
     */
    public Optional<String> fetchTranslatedField(String code, String targetField, String lang) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performFetchTranslatedField(normalizeCodeValue(code), targetField, lang);
    }

    protected abstract Optional<String> performFetchTranslatedField(@Nonnull String code,
                                                                    String targetField,
                                                                    String lang);

    /**
     * Normalizes the given code into the main code used by this table.
     * <p>
     * Lookup tables will most often provide multiple codes for the same entry (e.g. two letter and three letter ISO
     * codes for countries). This method checks all code and alias fields and resoves the given code into the leading
     * code used by this table so it can be used in the other methods provided.
     * <p>
     * Note that this method can also be used to verify if a code is valid at all.
     *
     * @param code the code to normalize
     * @return the normalized (leading) code or an empty optional if the code or alias is unknown
     */
    public Optional<String> normalize(String code) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return performNormalize(normalizeCodeValue(code));
    }

    protected abstract Optional<String> performNormalize(@Nonnull String code);

    /**
     * Performs a reverse lookup which transforms a given name into the leading code of this table.
     *
     * @param name the name (in any language) to be translated into a code
     * @return the leading code for the given name or an empty optional if the name is unknown
     */
    public Optional<String> reverseLookup(String name) {
        if (Strings.isEmpty(name)) {
            return Optional.empty();
        }

        return performReverseLookup(name);
    }

    protected abstract Optional<String> performReverseLookup(String name);

    /**
     * Normalizes user input (most commonly import data).
     * <p>
     * This actually just combines {@link #normalize(String)} with fallback to {@link #resolveName(String)} in
     * case a used re-imports data which has previously been exported using the name instead of the actual code.
     *
     * @param codeOrName the code or name to resolve into a leading code
     * @return the leading code for the given code or name or an empty optional if the value is unknown
     */
    public Optional<String> normalizeInput(String codeOrName) {
        Optional<String> normalizedCode = normalize(codeOrName);
        if (normalizedCode.isPresent()) {
            return normalizedCode;
        }

        return reverseLookup(codeOrName);
    }

    /**
     * Fetches the given object based on the data in the lookup table.
     * <p>
     * The system will try to use a cache if possible.
     *
     * @param type the type of value to instantiate (Must accept a JSONObject in its constructor)
     * @param code the leading code used to determine which value to load
     * @param <T>  the generic type of the object to fetch
     * @return the object for the given code or an empty optional if the code is unknown
     * @see #fetchObjectDirect(Class, String)
     */
    public <T> Optional<T> fetchObject(Class<T> type, String code) {
        if (Strings.isEmpty(type)) {
            return Optional.empty();
        }

        return performFetchObject(type, code, true);
    }

    /**
     * Fetches the given object based on the data in the lookup table - without using any cache.
     *
     * @param type the type of value to instantiate (Must accept a JSONObject in its constructor)
     * @param code the leading code used to determine which value to load
     * @param <T>  the generic type of the object to fetch
     * @return the object for the given code or an empty optional if the code is unknown
     * @see #fetchObject(Class, String)
     */
    public <T> Optional<T> fetchObjectDirect(Class<T> type, String code) {
        if (Strings.isEmpty(type)) {
            return Optional.empty();
        }

        return performFetchObject(type, code, false);
    }

    protected abstract <T> Optional<T> performFetchObject(Class<T> type, @Nonnull String code, boolean useCache);

    /**
     * Suggests several entries for the given search term using the currently active language.
     *
     * @param searchTerm the term used to filter the suggestions
     * @return a stream of suggestions for the given term. Note that most probably {@link Stream#limit(long)} should
     * be used on the result as this might yield quite a bunch of suggestions in order to optimize internal queries.
     */
    public Stream<LookupTableEntry> suggest(String searchTerm) {
        return suggest(searchTerm, NLS.getCurrentLang());
    }

    /**
     * Suggests several entries for the given search term using the given language.
     *
     * @param searchTerm the term used to filter the suggestions
     * @param lang       the language to translate the name and description to
     * @return a stream of suggestions for the given term. Note that most probably {@link Stream#limit(long)} should
     * be used on the result as this might yield quite a bunch of suggestions in order to optimize internal queries.
     */
    public Stream<LookupTableEntry> suggest(String searchTerm, String lang) {
        return performSuggest(new Limit(0, MAX_SUGGESTIONS), searchTerm, lang);
    }

    /**
     * Computes the actual suggestions for the given query and limit.
     *
     * @param limit      regulates how many suggestions are generated
     * @param searchTerm the term to search by
     * @param lang       the language to translate names and descriptions in
     * @return a stream containing all suggestions
     */
    protected abstract Stream<LookupTableEntry> performSuggest(Limit limit, String searchTerm, String lang);

    /**
     * Determines if this list is short enough to properly support {@link #scan()} or {@link #scan(String)}.
     *
     * @return <tt>true</tt> if scanning (listing all entries) is supported, <tt>false</tt> otherwise
     */
    public boolean canScan() {
        return supportsScan;
    }

    /**
     * Enumerates all entries in the table using the current language.
     *
     * @return a stream of all entries in this table or an empty stream is scanning isn't supported
     */
    public Stream<LookupTableEntry> scan() {
        return scan(NLS.getCurrentLang());
    }

    /**
     * Enumerates all entries in the table using the given language.
     *
     * @param lang the language to translate the name and description to
     * @return a stream of all entries in this table or an empty stream is scanning isn't supported
     */
    public Stream<LookupTableEntry> scan(String lang) {
        if (!canScan()) {
            return Stream.empty();
        }

        return performScan(lang);
    }

    protected abstract Stream<LookupTableEntry> performScan(String lang);
}
