/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.di.transformers.Composable;
import sirius.web.security.UserInfo;

/**
 * Holds a reference to the {@link UserInfo root user}, if currently spying another user or tenant.
 */
public class SpyUser {
    private final UserInfo rootUser;

    public SpyUser(UserInfo rootUser) {
        this.rootUser = rootUser;
    }

    public UserInfo getRootUser() {
        return rootUser;
    }
}
