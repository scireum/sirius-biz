/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.es.ElasticEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Stores security relevant events as "audit logs".
 */
@Framework(Protocols.FRAMEWORK_PROTOCOLS)
public class AuditLogEntry extends ElasticEntity {

    /**
     * Contains the timestamp when the event happened.
     */
    public static final Mapping TIMESTAMP = Mapping.named("timestamp");
    private LocalDateTime timestamp;

    /**
     * Contains the id of the tenant for which this event was recorded.
     */
    public static final Mapping TENANT = Mapping.named("tenant");
    @NullAllowed
    private String tenant;

    /**
     * Contains the name of the tenant for which this event was recorded.
     */
    public static final Mapping TENANT_NAME = Mapping.named("tenantName");
    @NullAllowed
    private String tenantName;

    /**
     * Contains the id of the account for which this event was recorded.
     */
    public static final Mapping USER = Mapping.named("user");
    @NullAllowed
    private String user;

    /**
     * Contains the name of the account for which this event was recorded.
     */
    public static final Mapping USER_NAME = Mapping.named("userName");
    @NullAllowed
    private String userName;

    /**
     * Determines if the entry represents an error / warning or an informative entry.
     */
    public static final Mapping NEGATIVE = Mapping.named("negative");
    private boolean negative;

    /**
     * Contains the IP address associated with this entry.
     */
    public static final Mapping IP = Mapping.named("ip");
    @NullAllowed
    private String ip;

    /**
     * Contains the actual message.
     */
    public static final Mapping MESSAGE = Mapping.named("message");
    @NullAllowed
    private String message;

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getTenant() {
        return tenant;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getUser() {
        return user;
    }

    public String getUserName() {
        return userName;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isNegative() {
        return negative;
    }

    public void setNegative(boolean negative) {
        this.negative = negative;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
