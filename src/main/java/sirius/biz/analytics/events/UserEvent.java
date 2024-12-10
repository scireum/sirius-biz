/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.mixing.Mapping;

/**
 * May be implemented by an {@link Event} to provide access to the {@link UserData} of the current user.
 */
public interface UserEvent {

    /**
     * Contains the user data of the event.
     */
    Mapping USER_DATA = Mapping.named("userData");

    /**
     * Returns the user data of the event.
     *
     * @return the user data of the event
     */
    UserData getUserData();
}
