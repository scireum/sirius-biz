/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides helpful constants and methods regarding language configuration data
 */
public class LanguageConstants {

    private LanguageConstants() {
    }

    public static List<String> getLanguages() {
        return Sirius.getSettings().getStringList("nls.languages");
    }

    public static List<String> getTranslatedLanguages() {
        return Sirius.getSettings().getStringList("nls.translatedLanguages");
    }

    public static List<String> getTranslatedBackendLanguages() {
        return Sirius.getSettings().getStringList("nls.translatedBackendLanguages");
    }

    /**
     * Gets the translated name of the given language using the current language.
     *
     * @param code the code of the language
     * @return the translated name of the given language
     */
    public static String getLanguageName(String code) {
        if (Strings.isEmpty(code)) {
            return "";
        }
        return NLS.get("Language." + code);
    }

    /**
     * Gets the translated names of the given languages using the current language
     *
     * @param languages the codes of the languages to translate
     * @return the translated names of the given languages joined together by ", "
     */
    public static String getLanguageNames(Collection<String> languages) {
        return languages.stream().map(LanguageConstants::getLanguageName).collect(Collectors.joining(", "));
    }
}
