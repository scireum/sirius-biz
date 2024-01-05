/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.kernel.async.CallContext;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;

/**
 * Provides some context once a rate limiting event occurs.
 */
public class RateLimitingInfo {

    private final String ip;
    private final String tenantId;
    private final String location;

    /**
     * Creates a new instance which represents the source / context which caused a rate limiting event.
     * <p>
     * Note that this is used for troubleshooting / reporting. Therefore some care should be taken to fill as many
     * values as possible. As this is only called once when a rate limit is hit (not for subesquent occurrences within
     * the same interval, some comutation effort can be invested - e.g. performing a database lookup to reveal the
     * causing tenant etc.).
     *
     * @param ip       the ip which caused the event
     * @param tenantId the tenant (id) which caused the event
     * @param location the location which caused the event
     */
    public RateLimitingInfo(@Nullable String ip, @Nullable String tenantId, @Nullable String location) {
        this.ip = ip;
        this.tenantId = tenantId;
        this.location = location;
    }

    /**
     * Boilerplate method to fill the IP and location from the given web context.
     *
     * @param ctx      the current request which caused the rate limiting event
     * @param tenantId the tenant id as this is most probably unknown / not determined yet
     * @return a rate limit info containing the remote IP and URI as location.
     */
    public static RateLimitingInfo fromWebContext(WebContext ctx, @Nullable String tenantId) {
        return new RateLimitingInfo(ctx.getRemoteIP().getHostAddress(), tenantId, ctx.getRequestedURI());
    }

    /**
     * Boilerplate method to fill the info object based on the current {@link CallContext}.
     *
     * <p>
     * The ip and location are drawn form the current {@link WebContext} (if available).
     * The tenant id is read from the {@link UserContext}.
     * <p>
     * Before using this method, please ensure that these have been properly initialized already. Rate limiting
     * should normally performed at an very early stage, so that some of the contexts might still be uninitialized.
     * In this case use {@link #fromWebContext(WebContext, String)} or the constructor itself.
     *
     * @return a rate limit info containing as much information as available
     */
    public static RateLimitingInfo fromCurrentContext() {
        UserInfo currentUser = UserContext.getCurrentUser();
        String tenantId = currentUser.isLoggedIn() ? currentUser.getTenantId() : null;
        WebContext webContext = WebContext.getCurrent();
        if (webContext.isValid()) {
            return new RateLimitingInfo(webContext.getRemoteIP().getHostAddress(),
                                        tenantId,
                                        webContext.getRequestedURI());
        } else {
            return new RateLimitingInfo(null, tenantId, null);
        }
    }

    public String getIp() {
        return ip;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getLocation() {
        return location;
    }
}
