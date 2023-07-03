/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.biz.analytics.events.Event;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Recorded once a rate limit was first hit for a realm, scope and interval.
 */
public class RateLimitingTriggeredEvent extends Event<RateLimitingTriggeredEvent> {

    /**
     * Contains the realm which defined the limit.
     */
    public static final Mapping REALM = Mapping.named("realm");
    private String realm;

    /**
     * Contains the scope which triggered the limit.
     */
    public static final Mapping SCOPE = Mapping.named("scope");
    private String scope;

    /**
     * Contains the effective limit which was reached.
     */
    public static final Mapping LIMIT = Mapping.named("limit");
    @Length(4)
    private Integer limit;

    /**
     * Contains the interval (in seconds) in which the limit was reached.
     */
    public static final Mapping INTERVAL = Mapping.named("interval");
    @Length(4)
    private Integer interval;

    /**
     * Contains the IP address (if available) which caused the event.
     */
    public static final Mapping IP = Mapping.named("ip");
    @NullAllowed
    private String ip;

    /**
     * Contains the tenant id (if available) which caused the event.
     */
    public static final Mapping TENANT = Mapping.named("tenant");
    @NullAllowed
    private String tenant;

    /**
     * Contains the location (if available) which caused the event.
     */
    @NullAllowed
    public static final Mapping LOCATION = Mapping.named("location");
    private String location;

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
