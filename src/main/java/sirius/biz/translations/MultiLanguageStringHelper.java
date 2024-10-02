/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.biz.importer.ImportHelper;
import sirius.biz.importer.ImporterContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a central helper when importing data for {@link MultiLanguageString} fields.
 * <p>
 * Basically we support two ways of importing data into a multi-language string field. Either this helper is
 * configured appropriately (using {@link #replaceOnImport()} , {@link #updateOnImport()} as well as
 * {@link #forceLanguage(String)} or {@link #withDefaultLanguage(String)}).
 * <p>
 * Alternatively. either {@link #createReplacement()} or {@link #createUpdate()}
 * are used to build {@link MultiLanguageStringValue} objects which are picked up by the
 * {@link MultiLanguageStringExtender} which will update the fields accordingly.
 */
public class MultiLanguageStringHelper extends ImportHelper {

    /**
     * Represents a builder pattern to set up a new value to be imported into a {@link MultiLanguageString} field.
     */
    public static class MultiLanguageStringValue {
        protected boolean replace;
        protected Map<String, String> data;

        protected MultiLanguageStringValue(boolean replace) {
            this.replace = replace;
        }

        /**
         * Specifies the new fallback value to use.
         *
         * @param value the new fallback value
         * @return the builder itself for fluent method calls
         */
        public MultiLanguageStringValue withFallback(String value) {
            return withText(MultiLanguageString.FALLBACK_KEY, value);
        }

        /**
         * Specifies a value to store for the given language
         *
         * @param language the language to store the value for
         * @param value    the new value for the given language
         * @return the builder itself for fluent method calls
         */
        public MultiLanguageStringValue withText(String language, String value) {
            if (data == null) {
                data = new HashMap<>();
            }

            data.put(language, value);

            return this;
        }

        /**
         * Fetches the text for the given language.
         *
         * @param language the language to fetch the text for
         * @return the text for the given language or an empty optional if no text is present
         */
        public Optional<String> fetchText(String language) {
            return Optional.ofNullable(data.get(language));
        }

        /**
         * Fetches the text for the given language or falls back to the fallback value.
         *
         * @param language the language to fetch the text for
         * @return the text for the given language or the fallback value if no text is present
         */
        public Optional<String> fetchTextOrFallback(String language) {
            return fetchText(language).or(() -> fetchText(MultiLanguageString.FALLBACK_KEY));
        }
    }

    private boolean replaceOnImport;
    private String forcedLanguage;
    private String defaultLanguage;

    /**
     * Creates a new instance for the given context.
     * <p>
     * Note that a helper must provide a public constructor with this signature.
     *
     * @param context the context for which this helper was created
     */
    public MultiLanguageStringHelper(ImporterContext context) {
        super(context);
    }

    /**
     * Creates a new value builder which replaces all values currently present in the multi-language string field.
     *
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi-language string field.
     */
    public static MultiLanguageStringValue createReplacement() {
        return new MultiLanguageStringValue(true);
    }

    /**
     * Creates a new value builder just like {@link #createReplacement()} which is filled with the given fallback value.
     *
     * @param fallbackValue the new fallback value to set
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi-language string field.
     */
    public static MultiLanguageStringValue createReplacement(String fallbackValue) {
        return new MultiLanguageStringValue(true).withFallback(fallbackValue);
    }

    /**
     * Creates a new value builder which enhances the underlying multi-language string field.
     *
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi-language string field.
     */
    public static MultiLanguageStringValue createUpdate() {
        return new MultiLanguageStringValue(false);
    }

    /**
     * Creates a new value builder just like {@link #createUpdate()} which is filled with the given fallback value.
     *
     * @param fallbackValue the new fallback value to set
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi-language string field.
     */
    public static MultiLanguageStringValue createUpdate(String fallbackValue) {
        return new MultiLanguageStringValue(false).withFallback(fallbackValue);
    }

    /**
     * Creates a new value builder just like {@link #createUpdate()} which is filled with the given text for the given
     * language.
     *
     * @param language the language to store the text for
     * @param value    the text value to store
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi-language string field.
     */
    public static MultiLanguageStringValue createUpdate(String language, String value) {
        return new MultiLanguageStringValue(false).withText(language, value);
    }

    /**
     * Creates a new value builder just like {@link #createUpdate()} which is filled with the given
     * {@link MultiLanguageString}.
     *
     * @param multiLanguageString the new {@link MultiLanguageString} to set
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi-language string field.
     */
    public static MultiLanguageStringValue createUpdate(MultiLanguageString multiLanguageString) {
        MultiLanguageStringValue value = new MultiLanguageStringValue(false);
        multiLanguageString.data().forEach(value::withText);
        return value;
    }

    /**
     * Overwrites all previously available translations when importing data into a multi-language string.
     *
     * @return the object itself for fluent calls
     */
    public MultiLanguageStringHelper replaceOnImport() {
        this.replaceOnImport = true;
        return this;
    }

    /**
     * Keeps previous translations in multi-language strings and only adds new ones during an import.
     *
     * @return the object itself for fluent calls
     */
    public MultiLanguageStringHelper updateOnImport() {
        this.replaceOnImport = false;
        return this;
    }

    /**
     * Specifies the target language to use <b>for all fields</b> when importing or exporting strings from or to
     * multi-language fields.
     * <p>
     * Note, if this is set, it will be used for all fields, even if these would have a <b>fallback</b> value to
     * update.
     *
     * @param forcedLanguage the language to use for all fields
     * @see #withDefaultLanguage(String)
     */
    public void forceLanguage(String forcedLanguage) {
        this.forcedLanguage = forcedLanguage;
    }

    /**
     * Specifies the target language to use <b>for fields without a fallback value</b>.
     * <p>
     * Note that this language is only used, if multi-language string field doesn't support default values. If no
     * explicit default language is specified, we use the current language of the user.
     *
     * @param defaultLanguage the language used for fields which do not accept a fallback value
     * @return the object itself for fluent calls
     */
    public MultiLanguageStringHelper withDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
        return this;
    }

    protected boolean isReplaceOnImport() {
        return replaceOnImport;
    }

    protected boolean hasForcedLanguage() {
        return Strings.isFilled(forcedLanguage);
    }

    protected String getForcedLanguage() {
        return forcedLanguage;
    }

    protected String getEffectiveLanguage() {
        if (Strings.isFilled(defaultLanguage)) {
            return defaultLanguage;
        } else {
            return NLS.getCurrentLanguage();
        }
    }
}
