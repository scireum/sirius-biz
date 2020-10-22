/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.biz.model.LoginData;
import sirius.db.mixing.Mapping;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;

/**
 * Records the activity (presence) of a user per day.
 * <p>
 * This can be used to mark days on which a user was active so that the activity level can be computed.
 * {@link UserManager UserManagers} can use {@link LoginData#LAST_SEEN} to control that roughly only one event
 * is created per user and day.
 * <p>
 * To record and trace the activity of a user in a detailed manner, use {@link PageImpressionEvent}.
 *
 * @see sirius.biz.tenants.TenantUserManager#recordUserActivityEvent(UserInfo)
 */
public class UserActivityEvent extends Event {

    /**
     * Contains the current user, tenant and scope if available.
     */
    public static final Mapping USER_DATA = Mapping.named("userData");
    private final UserData userData = new UserData();

    /**
     * Contains metadata about the HTTP request (user-agent, url).
     */
    public static final Mapping WEB_DATA = Mapping.named("webData");
    private final WebData webData = new WebData();

    public UserData getUserData() {
        return userData;
    }

    public WebData getWebData() {
        return webData;
    }
}
