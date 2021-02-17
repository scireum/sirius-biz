/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import com.alibaba.fastjson.JSONObject;
import sirius.db.mixing.types.SafeMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides a language-text map as property value.
 *
 * @see MultiLanguageStringProperty
 */
public class MultiLanguageString extends SafeMap<String, String> {

    /**
     * Represents the fake language code used to store the fallback value.
     * <p>
     * This value is used if a concrete translation is missing (if {@link #isWithFallback()} is <tt>true</tt>).
     */
    public static final String FALLBACK_KEY = "fallback";

    private List<String> validLanguages = Collections.emptyList();
    private String i18nPermission;
    private boolean withFallback;

    /**
     * Creates a new object to hold a language-text map with no place for a fallback string.
     */
    public MultiLanguageString() {
        this.withFallback = false;
    }

    /**
     * Creates a new object to hold a language-text map validating against the given set of language codes
     * with no place for a fallback string.
     *
     * @param validLanguages set of language codes to validate against
     * @deprecated use fluent api instead ({@link MultiLanguageString#withValidLanguages(List)})
     */
    @Deprecated
    public MultiLanguageString(@Nonnull List<String> validLanguages) {
        this.withFallback = false;
        this.validLanguages = Collections.unmodifiableList(validLanguages);
    }

    /**
     * Creates a new object to hold a language-text map.
     *
     * @param withFallback if a fallback should also be stored in the map
     * @deprecated use fluent api instead ({@link MultiLanguageString#withFallback})
     */
    @Deprecated
    public MultiLanguageString(boolean withFallback) {
        this.withFallback = withFallback;
    }

    /**
     * Creates a new object to hold a language-text map validating against the given set of language codes.
     *
     * @param withFallback   if a fallback should also be stored in the map
     * @param validLanguages list of language codes to validate against
     * @deprecated use fluent api instead ({@link MultiLanguageString#withFallback}, {@link MultiLanguageString#withValidLanguages(List)})
     */
    @Deprecated
    public MultiLanguageString(boolean withFallback, @Nonnull List<String> validLanguages) {
        this.withFallback = withFallback;
        this.validLanguages = new ArrayList<>(validLanguages);
    }

    /**
     * Enables fallback for this MultiLanguageString.
     *
     * @return the object itself for fluent method calls
     */
    public MultiLanguageString withFallback() {
        this.withFallback = true;
        return this;
    }

    /**
     * Sets the given set of valid languages.
     *
     * @param validLanguages the list of language codes to validate against
     * @return the object itself for fluent method calls
     */
    public MultiLanguageString withValidLanguages(@Nonnull List<String> validLanguages) {
        this.validLanguages = Collections.unmodifiableList(validLanguages);
        return this;
    }

    /**
     * Sets the given internationalization permission.
     *
     * @param i18nPermission the permission required to change entries other than 'fallback'
     * @return the object itself for fluent method calls
     */
    public MultiLanguageString withI18nPermission(@Nonnull String i18nPermission) {
        this.i18nPermission = i18nPermission;
        return this;
    }

    public List<String> getValidLanguages() {
        return Collections.unmodifiableList(validLanguages);
    }

    public String getI18nPermission() {
        return i18nPermission;
    }

    @Override
    protected boolean valueNeedsCopy() {
        return false;
    }

    @Override
    protected String copyValue(String value) {
        return value;
    }

    /**
     * Adds a new text using the language defined by {@link NLS#getCurrentLang()}.
     * <p>
     * If a null text is given it will be ignored, if the list already contains an entry it will be removed.
     *
     * @param text the text associated with the language
     * @return the object itself for fluent method calls
     * @throws sirius.kernel.health.HandledException if the current language is invalid
     */
    public MultiLanguageString addText(String text) {
        return addText(NLS.getCurrentLang(), text);
    }

    /**
     * Adds a new text for the given language.
     * <p>
     * If a null text is given it will be ignored, if the list already contains an entry it will be removed.
     *
     * @param language the language code
     * @param text     the text associated with the language
     * @return the object itself for fluent method calls
     * @throws sirius.kernel.health.HandledException if the provided language code is invalid
     */
    public MultiLanguageString addText(String language, String text) {
        put(language, text);
        return this;
    }

    /**
     * Adds the given text as a fallback to the map.
     * <p>
     * If a null text is given it will be ignored, if the list already contains an entry it will be removed.
     *
     * @param text the text to be used as fallback
     * @return the object itself for fluent method calls
     * @throws IllegalStateException if this field does not support fallbacks
     * @deprecated use <tt>setFallback</tt>
     */
    @Deprecated
    public MultiLanguageString addFallback(String text) {
        return setFallback(text);
    }

    /**
     * Adds the given text as a fallback to the map.
     * <p>
     * If a null text is given it will be ignored, if the list already contains an entry it will be removed.
     *
     * @param text the text to be used as fallback
     * @return the object itself for fluent method calls
     * @throws IllegalStateException if this field does not support fallbacks
     */
    public MultiLanguageString setFallback(String text) {
        if (!withFallback && Strings.isFilled(text)) {
            throw new IllegalStateException(
                    "Can not call addFallback on a MultiLanguageString without fallback enabled.");
        }
        return addText(FALLBACK_KEY, text);
    }

    /**
     * Returns the fallback value.
     *
     * @return the fallback value or <tt>null</tt> if there is none.
     */
    @Nullable
    public String getFallback() {
        return data().get(FALLBACK_KEY);
    }

    /**
     * Checks if a text exists for a given language.
     *
     * @param language the language code
     * @return <tt>true</tt> when a text exists, otherwise <tt>false</tt>
     */
    public boolean hasText(String language) {
        return data().containsKey(language);
    }

    /**
     * Returns an optional text associated with the current language defined by {@link NLS#getCurrentLang()}.
     * <p>
     * If no text for the language exists and a fallback is defined, the fallback is returned.
     *
     * @return an Optional String containing the text, otherwise an empty Optional
     */
    @Nonnull
    public Optional<String> getText() {
        return getText(NLS.getCurrentLang());
    }

    /**
     * Returns an optional text associated with a given language.
     * <p>
     * If no text for the language exists and a fallback is defined, the fallback is returned.
     *
     * @param language the language code
     * @return an Optional String containing the text, otherwise an empty Optional
     */
    @Nonnull
    public Optional<String> getText(String language) {
        if (!hasText(language)) {
            if (hasFallback()) {
                return Optional.of(data().get(FALLBACK_KEY));
            }
            return Optional.empty();
        }
        return Optional.of(fetchText(language));
    }

    /**
     * Returns the text associated with the current language defined by {@link NLS#getCurrentLang()}.
     * <p>
     * Please note that the defined fallback is never used, use {@link #fetchTextOrFallback()} instead.
     *
     * @return the text if it exists, otherwise <tt>null</tt>
     */
    @Nullable
    public String fetchText() {
        return data().get(NLS.getCurrentLang());
    }

    /**
     * Returns the text associated with a given language.
     * <p>
     * Please note that the defined fallback is never used, use {@link #fetchTextOrFallback(String)} instead.
     *
     * @param language the language code
     * @return the text if it exists, otherwise <tt>null</tt>
     */
    @Nullable
    public String fetchText(String language) {
        return data().get(language);
    }

    /**
     * Returns the text associated with a given language, falling back to an alternative language when not found.
     *
     * @param language         the language code
     * @param fallbackLanguage the alternative language code
     * @return the text found under <tt>language</tt>, if none found under <tt>fallbackLanguage</tt> or <tt>null</tt> otherwise
     */
    @Nullable
    public String fetchText(String language, String fallbackLanguage) {
        return data().getOrDefault(language, fetchText(fallbackLanguage));
    }

    /**
     * Returns the text associated with with the current language defined by {@link NLS#getCurrentLang()}, falling back to the saved fallback.
     *
     * @return the text found under <tt>language</tt>, if none found the one from {@link #FALLBACK_KEY} is returned
     */
    @Nullable
    public String fetchTextOrFallback() {
        return fetchTextOrFallback(NLS.getCurrentLang());
    }

    /**
     * Returns the text associated with a given language, falling back to the saved fallback.
     *
     * @param language the language code
     * @return the text found under <tt>language</tt>, if none found the one from {@link #FALLBACK_KEY} is returned
     */
    @Nullable
    public String fetchTextOrFallback(String language) {
        if (!withFallback) {
            throw new IllegalStateException(
                    "Can not call fetchTextOrFallback on a MultiLanguageString without fallback enabled.");
        }
        return data().getOrDefault(language, data().get(FALLBACK_KEY));
    }

    private boolean hasFallback() {
        return withFallback && containsKey(FALLBACK_KEY);
    }

    public boolean isWithFallback() {
        return withFallback;
    }

    /**
     * Puts the given key and value into the map.
     * <br>
     * If a null text is given it will be ignored, if the list already contains an entry it will be removed.
     *
     * @param key   the key used to store the value
     * @param value the value to store
     * @return the map itself for fluent method calls
     */
    @Override
    public SafeMap<String, String> put(@Nonnull String key, String value) {
        if (Strings.isFilled(value)) {
            super.modify().put(key, value);
        } else {
            super.modify().remove(key);
        }
        return this;
    }

    @Override
    public void setData(Map<String, String> newData) {
        // remove keys with null values first
        super.setData(newData.entrySet()
                             .stream()
                             .filter(entry -> entry.getValue() != null)
                             .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Direct modifications of the underlying map are not allowed. Therefore upon calling a {@link UnsupportedOperationException} will be thrown.
     * <br>
     * Please use one of the other methods to modify the underlying map:
     * <ul>
     *     <li>{@link MultiLanguageString#addText(String)}</li>
     *     <li>{@link MultiLanguageString#addText(String, String)}</li>
     *     <li>{@link MultiLanguageString#setData(Map)}</li>
     *     <li>{@link MultiLanguageString#put(String, String)}</li>
     *     <li>{@link MultiLanguageString#remove(String)}</li>
     *     <li>{@link MultiLanguageString#clear()}
     * </ul>
     *
     * @return throws an {@link UnsupportedOperationException}
     */
    @Override
    public Map<String, String> modify() {
        String className = getClass().getName();
        throw new UnsupportedOperationException(className
                                                + " does not support modify. Please use "
                                                + className
                                                + ".remove, "
                                                + className
                                                + ".put, "
                                                + className
                                                + ".addText or "
                                                + className
                                                + ".addFallback.");
    }

    /**
     * Removes the given language key from the list of languages.
     *
     * @param languageKey the language key to be removed from the underlying list of languages.
     */
    public void remove(String languageKey) {
        super.modify().remove(languageKey);
    }

    /**
     * Transforms the multilanguage map into an JSON object.
     *
     * @return Multilanguage map as a JSON object
     */
    public String getAsJSON() {
        JSONObject jsonObject = new JSONObject();
        if (data != null) {
            jsonObject.putAll(data);
        }
        return jsonObject.toJSONString();
    }
}
