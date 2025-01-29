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
    public static final Mapping LOCATION = Mapping.named("location");
    @NullAllowed
    private String location;

    /**
     * Sets the realm which defined the limit.
     *
     * @param realm the realm
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withRealm(String realm) {
        this.realm = realm;
        return this;
    }

    /**
     * Sets the scope which triggered the limit
     *
     * @param scope the scope
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withScope(String scope) {
        this.scope = scope;
        return this;
    }

    /**
     * Sets the effective limit which was reached.
     *
     * @param limit the effective limit
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withLimit(Integer limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Sets the interval (in seconds) in which the limit was reached.
     *
     * @param interval the interval
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withInterval(Integer interval) {
        this.interval = interval;
        return this;
    }

    /**
     * Sets the IP address which caused the event.
     *
     * @param ip the IP address
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withIp(String ip) {
        this.ip = ip;
        return this;
    }

    /**
     * Sets the tenant which caused the event.
     *
     * @param tenant the tenant identifier
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withTenant(String tenant) {
        this.tenant = tenant;
        return this;
    }

    /**
     * Sets the location which caused the event.
     *
     * @param location the location
     * @return the event itself for fluent method calls
     */
    public RateLimitingTriggeredEvent withLocation(String location) {
        this.location = location;
        return this;
    }

    public String getRealm() {
        return realm;
    }

    public String getScope() {
        return scope;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getInterval() {
        return interval;
    }

    public String getIp() {
        return ip;
    }

    public String getTenant() {
        return tenant;
    }

    public String getLocation() {
        return location;
    }
}
