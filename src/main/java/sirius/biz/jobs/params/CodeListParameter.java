/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.codelists.CodeList;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.nls.NLS;

/**
 * Permits to select a {@link CodeList} as parameter.
 */
public abstract class CodeListParameter<I, L extends BaseEntity<I> & CodeList>
        extends EntityParameter<L, CodeListParameter<I, L>> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public CodeListParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    public CodeListParameter(String name) {
        super(name);
    }

    /**
     * Returns the autocompletion URL used to determine suggestions for inputs provided by the user.
     *
     * @return the autocomplete URL used to provide suggestions for user input
     */
    @Override
    public String getAutocompleteUri() {
        return "/code-lists/autocomplete/";
    }
}
