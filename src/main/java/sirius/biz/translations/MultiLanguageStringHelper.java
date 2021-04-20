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

/**
 * Provides a central helper when importing data for {@link MultiLanguageString} fields.
 * <p>
 * Basically we support two ways of importing data into a multi language string field. Either this helper is
 * configured appropriately (using {@link #setReplaceOnImport(boolean)}, {@link #setImportLanguage(String)}, or
 * {@link #setDefaultImportLanguage(String)}) or either {@link #createReplacement()} or {@link #createUpdate()}
 * are used to build {@link MultiLanguageStringValue} objects which are picked up by the
 * {@link MultiLanguageStringExtender} which will update the fields accordingly.
 * <p>
 * For exporting data, {@link #setExportLanguage(String)} and {@link #setDefaultExportLanguage(String)} can be used
 * to control which values are actually exported.
 */
public class MultiLanguageStringHelper extends ImportHelper {

    /**
     * Represents a builder pattern to setup a new value to be imported into a {@link MultiLanguageString} field.
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
         * @param lang  the language to store the value for
         * @param value the new value for the given language
         * @return the builder itself for fluent method calls
         */
        public MultiLanguageStringValue withText(String lang, String value) {
            if (data == null) {
                data = new HashMap<>();
            }

            data.put(lang, value);

            return this;
        }
    }

    private boolean replaceOnImport;
    private String importLanguage;
    private String defaultImportLanguage;
    private String exportLanguage;
    private String defaultExportLanguage;

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
     * Creates a new value builder which replaces all values currently present in the multi language string field.
     *
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi language string field.
     */
    public static MultiLanguageStringValue createReplacement() {
        return new MultiLanguageStringValue(true);
    }

    /**
     * Creates a new value builder just like {@link #createReplacement()} which is filled with the given fallback value.
     *
     * @param fallbackValue the new fallback value to set
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi language string field.
     */
    public static MultiLanguageStringValue createReplacement(String fallbackValue) {
        return new MultiLanguageStringValue(false).withFallback(fallbackValue);
    }

    /**
     * Creates a new value builder which enhances the underlying multi language string field.
     *
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi language string field.
     */
    public static MultiLanguageStringValue createUpdate() {
        return new MultiLanguageStringValue(false);
    }

    /**
     * Creates a new value builder just like {@link #createUpdate()} which is filled with the given fallback value.
     *
     * @param fallbackValue the new fallback value to set
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi language string field.
     */
    public static MultiLanguageStringValue createUpdate(String fallbackValue) {
        return new MultiLanguageStringValue(false).withFallback(fallbackValue);
    }

    /**
     * Creates a new value builder just like {@link #createUpdate()} which is filled with the given text for the given
     * language.
     *
     * @param lang  the language to store the text for
     * @param value the text value to store
     * @return a new builder which can be directly put into the {@link sirius.biz.importer.ImportContext} to update
     * a multi language string field.
     */
    public static MultiLanguageStringValue createUpdate(String lang, String value) {
        return new MultiLanguageStringValue(false).withText(lang, value);
    }

    /**
     * Determines if a multi language string field should be cleared before importing new data.
     * <p>
     * If the import is used to add translations to existing data, this should be set to <tt>false</tt>. If the
     * import replaces all existing data, this should be set to <tt>true</tt>.
     *
     * @param replaceOnImport <tt>true</tt> to remove all data before applying the new one, <tt>false</tt> to keep
     *                        existing values (unless overwritten during the import).
     */
    public void setReplaceOnImport(boolean replaceOnImport) {
        this.replaceOnImport = replaceOnImport;
    }

    /**
     * Specifies the target language to use <b>for all fields</b> when importing strings into multi language fields.
     * <p>
     * Note, if this is set, it will be used for all fields, even if these would have a <b>fallback</b> value to
     * update.
     *
     * @param importLanguage the import language to use for all fields
     * @see #setDefaultImportLanguage(String)
     */
    public void setImportLanguage(String importLanguage) {
        this.importLanguage = importLanguage;
    }

    /**
     * Specifies the target language to use <b>for fields without a fallback value</b>.
     * <p>
     * Note that this language is only used, if we cannot update hte <b>fallback value</b> (which is suppressed for
     * some multi language fields). If no explicit default import language is specified, we use the current
     * language of the user.
     *
     * @param defaultImportLanguage the language used to import strings into fields which do not accept a fallback value
     */
    public void setDefaultImportLanguage(String defaultImportLanguage) {
        this.defaultImportLanguage = defaultImportLanguage;
    }

    /**
     * Specifies the export language to use <b>for all fields</b>.
     *
     * If speciied, we export the value for this language for all multi language strings, even if a <b>fallback</b>
     * value would be present.
     *
     * @param exportLanguage the export language to use for all fields
     * @see #setDefaultExportLanguage(String)
     */
    public void setExportLanguage(String exportLanguage) {
        this.exportLanguage = exportLanguage;
    }

    /**
     * Specifies the export language to use <b>for fields which do not support a fallback value</b>.
     *
     * @param defaultExportLanguage the language to export if no <b>fallback</b> is present
     */
    public void setDefaultExportLanguage(String defaultExportLanguage) {
        this.defaultExportLanguage = defaultExportLanguage;
    }

    protected boolean isReplaceOnImport() {
        return replaceOnImport;
    }

    protected String getImportLanguage() {
        return importLanguage;
    }

    protected String getEffectiveImportLanguage() {
        if (Strings.isFilled(defaultImportLanguage)) {
            return defaultImportLanguage;
        } else {
            return NLS.getCurrentLang();
        }
    }

    protected String getExportLanguage() {
        return exportLanguage;
    }

    protected String getEffectiveExportLanguage() {
        if (Strings.isFilled(defaultExportLanguage)) {
            return defaultExportLanguage;
        } else {
            return NLS.getCurrentLang();
        }
    }
}
