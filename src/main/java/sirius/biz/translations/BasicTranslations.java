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
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.di.std.ConfigValue;

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

    protected BasicTranslations(BaseEntity<?> owner) {
        this.owner = owner;
    }

    /**
     * provides a validator so translations can only be added for supported languages.
     *
     * @param lang the language code in question
     * @return true if language is supported by the system, false otherwise
     */
    protected boolean isSupportedLanguage(String lang) {
        //TODO: compare to existing codes - e.g. return LanguageCodeList.codes.contains(lang);, or use config like with MultiLanguageStrings
        supportedLanguages.contains(lang);
        return true;
    }

    /**
     * Updates the translation for the given field and language, or adds a new translation if it does not exist
     * <p>
     * Note that an existing translation is deleted when calling this with empty text
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @param text  translated text for the given language
     */
    public abstract void updateText(Mapping field, String lang, String text);

    /**
     * Returns the translation for the given parameters, or creates a new one if none exists.
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @param text  translated text for the given language
     * @return the translation entity matching the database in use (Mongo or SQL)
     */
    protected abstract T findOrCreateTranslation(Mapping field, String lang, String text);

    /**
     * Gets the translated text for the given field and language.
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @return translated text for the given language
     */
    public String getText(Mapping field, String lang) {
        Optional<T> translation = fetchTranslation(field, lang);
        if (translation.isPresent()) {
            return translation.get().getTranslationData().getText();
        } else {
            //TODO: if no translation present for given lang, use fallback, or default from parent
            return "";
        }
    }

    /**
     * Looks up the translation entity for the database in use (e.g. MongoDB or SQL).
     *
     * @param field {@link Mapping} of the translated field
     * @param lang  language code
     * @return the resolved translation entity wrapped as optional or an empty optional if the value couldn't be resolved
     */
    protected abstract Optional<T> fetchTranslation(Mapping field, String lang);
}
