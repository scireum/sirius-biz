/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.web.controller.AutocompleteHelper;

/**
 * Represents an entry within a {@link LookupTable}.
 * <p>
 * This is mainly used by {@link LookupTable#suggest(String)} and {@link LookupTable#scan(String)}.
 */
public class LookupTableEntry {

    private final String code;
    private final String name;
    private final String description;

    /**
     * Creates a new entry.
     *
     * @param code        the code of the entry
     * @param name        the name of the entry
     * @param description a description of the entry
     */
    public LookupTableEntry(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return code + " (" + name + ")";
    }

    /**
     * Transforms this entry into a completion to be supplied to a {@link AutocompleteHelper}.
     *
     * @param fieldDisplay      the  {@link sirius.biz.codelists.LookupValue.Display display mode} for the field label
     * @param completionDisplay the  {@link sirius.biz.codelists.LookupValue.Display display mode} for the completion label
     * @return the completion which represents this entry
     */
    public AutocompleteHelper.Completion toAutocompletion(LookupValue.Display fieldDisplay,
                                                          LookupValue.Display completionDisplay) {
        return AutocompleteHelper.suggest(code)
                                 .withFieldLabel(fieldDisplay.makeDisplayString(this))
                                 .withCompletionLabel(completionDisplay.makeDisplayString(this))
                                 .withCompletionDescription(description);
    }
}
