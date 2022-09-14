/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.analytics.events.Event;
import sirius.biz.analytics.events.UserData;
import sirius.db.mixing.Mapping;

/**
 * Records a click on an {@link UpdateInfo}.
 *
 * @see UpdateManager
 */
public class UpdateClickEvent extends Event {

    public static final Mapping UPDATE_GUID = Mapping.named("updateGuid");
    private String updateGuid;

    /**
     * Contains the current user, tenant and scope if available.
     */
    public static final Mapping USER_DATA = Mapping.named("userData");
    private final UserData userData = new UserData();

    /**
     * Specifies the {@link UpdateInfo#getGuid} to record a click event for.
     *
     * @param updateGuid the id of the update
     * @return the event itself for fluent method calls
     */
    public UpdateClickEvent forUpdateGuid(String updateGuid) {
        this.updateGuid = updateGuid;
        return this;
    }

    public String getUpdateGuid() {
        return updateGuid;
    }

    public UserData getUserData() {
        return userData;
    }
}
