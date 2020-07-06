/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.FieldLookupCache;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provides database independent specifications for functionality to access and edit translations for an entity.
 *
 * @param <T> the translation entity type used by a concrete subclass
 */
public abstract class BasicTranslations<T extends BaseEntity<?> & Translation> extends Composite {

    @Transient
    protected BaseEntity<?> owner;

    @Transient
    @ConfigValue("mixing.multiLanguageStrings.supportedLanguages")
    protected static Set<String> supportedLanguages;

    @Transient
    @Part
    private static FieldLookupCache fieldLookupCache;

    protected BasicTranslations(BaseEntity<?> owner) {
        this.owner = owner;
    }

    /**
     * Provides a validator so translations can only be added for supported languages.
     *
     * @param lang the language code in question
     * @return true if language is supported by the system, false otherwise
     */
    protected boolean isSupportedLanguage(String lang) {
        return supportedLanguages.contains(lang);
    }

    /**
     * Updates the translation for the given field and language, or adds a new translation if it does not exist.
     * <p>
     * Note that an existing translation is deleted when calling this with empty text.
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @param text  translated text for the given language
     */
    public abstract void updateText(Mapping field, String lang, String text);

    /**
     * Deletes the translation for the given field and language
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  code of the language to be deleted
     */
    public abstract void deleteText(Mapping field, String lang);

    /**
     * Deletes all translations for the given field
     *
     * @param field {@link Mapping} of the translated field
     */
    public abstract void deleteAllTexts(Mapping field);

    /**
     * Checks, if the given language is supported by the system and returns the translation for the given parameters,
     * or creates a new one if none exists.
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @param text  translated text for the given language
     * @return the translation entity matching the database in use (Mongo or SQL), null if language not supported
     */
    protected abstract T findOrCreateTranslation(Mapping field, String lang, String text);

    /**
     * Gets the translated text for the given field and language.
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @return {@link Optional} with translated text for the given language, or empty if none is found
     */
    public Optional<String> getText(Mapping field, String lang) {
        if (!isSupportedLanguage(lang)) {
            return Optional.empty();
        }

        Optional<T> translation = fetchTranslation(field, lang);
        return translation.map(t -> t.getTranslationData().getText());
    }

    /**
     * Returns the translated text for the given field and language. Uses fallback language if no translation is found.
     * If the fallback also has no translation, the default text from the owner entity is returned.
     *
     * @param field        {@link Mapping} of the translated field
     * @param lang         language code
     * @param fallbackLang language code for fallback
     * @return translated text for the given language, fallback, or the default text from owner entity
     */
    public String getRequiredText(Mapping field, String lang, String fallbackLang) {
        Optional<String> text = getText(field, lang);
        if (!text.isPresent()) {
            text = getText(field, fallbackLang);
            if (!text.isPresent()) {
                return fetchDefaultText(field);
            }
        }
        return text.get();
    }

    /**
     * Fetches the default text by querying {@link sirius.db.mixing.FieldLookupCache} for the owner entity's given field.
     *
     * @param field {@link Mapping field} to fetch the the text from
     * @return the text as String, or null if lookup was unsuccessful
     */
    protected String fetchDefaultText(Mapping field) {
        return fieldLookupCache.lookup(owner.getClass(), owner.getId(), field).asString();
    }

    /**
     * Returns all available translated texts for the given field.
     *
     * @param field {@link Mapping} of the translated field
     * @return a {@link Map} of all translated texts with corresponding language code as key, empty map if none present
     */
    public Map<String, String> getAllTexts(Mapping field) {
        Optional<List<T>> translations = fetchAllTranslations(field);
        if (!translations.isPresent()) {
            return new HashMap<>();
        }
        Map<String, String> translatedTexts = new HashMap<>();
        for (T t : translations.get()) {
            translatedTexts.put(t.getTranslationData().getLang(), t.getTranslationData().getText());
        }
        return translatedTexts;
    }

    /**
     * Looks up the translation entity for the database in use (e.g. MongoDB or SQL).
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @return the resolved translation entity wrapped as optional or an empty optional if the value couldn't be resolved
     */
    protected abstract Optional<T> fetchTranslation(Mapping field, String lang);

    /**
     * Looks up and returns all available {@link Translation translation} entities for the given field
     *
     * @param field {@link Mapping} of the translated field
     * @return {@link Optional} with a {@link java.util.List} of all {@link Translation translations}, or empty if none are found
     */
    protected abstract Optional<List<T>> fetchAllTranslations(Mapping field);
}
