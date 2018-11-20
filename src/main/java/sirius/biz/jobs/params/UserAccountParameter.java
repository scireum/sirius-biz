/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.tenants.UserAccount;
import sirius.kernel.nls.NLS;

public class UserAccountParameter extends EntityParameter<UserAccount> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public UserAccountParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    public UserAccountParameter(String name) {
        super(name);
    }

    @Override
    public String getAutocompleteUri() {
        return "/user-accounts/autocomplete";
    }

    @Override
    protected Class<UserAccount> getType() {
        return UserAccount.class;
    }
}
