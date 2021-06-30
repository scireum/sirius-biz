/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Framework;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stores security relevant events as "audit logs".
 */
@Framework(Protocols.FRAMEWORK_PROTOCOLS)
public class AuditLogEntry extends SearchableEntity {

    /**
     * Contains the timestamp when the event happened.
     */
    public static final Mapping TIMESTAMP = Mapping.named("timestamp");
    private LocalDateTime timestamp;

    /**
     * Contains the date when the event happened.
     * <p>
     * We use this to de-duplicate positive events (e.g. API calls).
     */
    public static final Mapping DATE = Mapping.named("date");
    @NullAllowed
    private LocalDate date;

    /**
     * Contains the id of the tenant for which this event was recorded.
     */
    public static final Mapping TENANT = Mapping.named("tenant");
    @NullAllowed
    @SearchContent
    private String tenant;

    /**
     * Contains the name of the tenant for which this event was recorded.
     */
    public static final Mapping TENANT_NAME = Mapping.named("tenantName");
    @NullAllowed
    @SearchContent
    private String tenantName;

    /**
     * Contains the id of the account for which this event was recorded.
     */
    public static final Mapping USER = Mapping.named("user");
    @NullAllowed
    @SearchContent
    private String user;

    /**
     * Contains the name of the account for which this event was recorded.
     */
    public static final Mapping USER_NAME = Mapping.named("userName");
    @NullAllowed
    @SearchContent
    private String userName;

    /**
     * Contains the id of the account which caused the event.
     */
    public static final Mapping CAUSED_BY_USER = Mapping.named("causedByUser");
    @NullAllowed
    @SearchContent
    private String causedByUser;

    /**
     * Contains the name of the account for which this event was recorded.
     */
    public static final Mapping CAUSED_BY_USER_NAME = Mapping.named("causedByUserName");
    @NullAllowed
    @SearchContent
    private String causedByUserName;

    /**
     * Determines if the entry is hidden from the user / tenant for which it was recorded.
     * <p>
     * Such events are only visible to the system tenant.
     */
    public static final Mapping HIDDEN = Mapping.named("hidden");
    private boolean hidden;

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
    @SearchContent
    private String ip;

    /**
     * Contains the actual message.
     */
    public static final Mapping MESSAGE = Mapping.named("message");
    @NullAllowed
    @SearchContent
    private String message;

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
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

    public String getCausedByUser() {
        return causedByUser;
    }

    public void setCausedByUser(String causedByUser) {
        this.causedByUser = causedByUser;
    }

    public String getCausedByUserName() {
        return causedByUserName;
    }

    public void setCausedByUserName(String causedByUserName) {
        this.causedByUserName = causedByUserName;
    }
}
