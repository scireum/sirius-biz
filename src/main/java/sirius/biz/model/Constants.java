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

import java.util.List;

/**
 * Provides helpful constants and methods regarding language configuration data
 * (and known currencies, countries, ... coming soon™)
 */
public class Constants {

    private Constants() {
    }

    public static List<String> getLanguages() {
        return Sirius.getSettings().getStringList("nls.languages");
    }

    /**
     * Gets the translated name of the give language using the current language.
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
}
