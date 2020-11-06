/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.nls.NLS;

import java.util.List;

/**
 * Defines a {@link SelectStringParameter} to select and translate Languages for export/import actions.
 */
public class LanguageParameter extends SelectStringParameter {

    public static final String PARAMETER_NAME = "locale_lang";

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public LanguageParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public List<Tuple<String, String>> getValues() {
        NLS.getSupportedLanguages().forEach(language -> withEntry(language, getLanguageName(language)));
        return super.getValues();
    }

    private String getLanguageName(String code) {
        if (Strings.isEmpty(code)) {
            return "";
        }
        return NLS.get("Language." + code);
    }
}
