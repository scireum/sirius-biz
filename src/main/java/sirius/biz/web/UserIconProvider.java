/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import java.util.Optional;

/**
 * Permits determining which avatar / icon to show for a user.
 * <p>
 * The Tycho UI adapts a {@link sirius.web.security.UserInfo} into this in order to determine which icon to
 * user for the user menu.
 */
public interface UserIconProvider {

    /**
     * Returns the path to the icon to use
     *
     * @return the asset URI to the icon to use
     */
    Optional<String> getUserIcon();
}
