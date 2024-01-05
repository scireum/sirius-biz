/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.es.Elastic;
import sirius.kernel.Sirius;
import sirius.kernel.di.Initializable;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Inserts entries into the security log of the system.
 */
@Register(classes = {AuditLog.class, Initializable.class})
public class AuditLog implements Initializable {

    @Part
    private Elastic elastic;

    private boolean enabled;

    private static final Log LOG = Log.get("audit");

    @Override
    public void initialize() throws Exception {
        enabled = Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_PROTOCOLS);
    }

    /**
     * Fluent builder for audit logs.
     */
    public class AuditLogBuilder {

        private final AuditLogEntry entry = new AuditLogEntry();

        protected AuditLogBuilder(String message, boolean negative) {
            entry.setTimestamp(LocalDateTime.now());
            entry.setDate(LocalDate.now());
            entry.setNegative(negative);
            entry.setMessage(message);
            entry.setIp(getCurrentIP());
        }

        private String getCurrentIP() {
            WebContext webContext = WebContext.getCurrent();
            if (webContext.isValid()) {
                return webContext.getRemoteIP().getHostAddress();
            }

            return null;
        }

        /**
         * Creates the entry for the current user in {@link UserContext#getCurrentUser()}.
         *
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder forCurrentUser() {
            UserInfo userInfo = UserContext.getCurrentUser();
            return forUser(userInfo.getUserId(), userInfo.getProtocolUsername()).forTenant(userInfo.getTenantId(),
                                                                                           userInfo.getTenantName());
        }

        /**
         * Creates the entry for the given user.
         *
         * @param userId the ID of the user
         * @param name   the name of the user
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder forUser(@Nullable String userId, @Nullable String name) {
            entry.setUser(userId);
            entry.setUserName(name);

            return this;
        }

        /**
         * Marks the entry as caused by the current user in {@link UserContext#getCurrentUser()}.
         *
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder causedByCurrentUser() {
            UserInfo userInfo = UserContext.getCurrentUser();
            return causedByUser(userInfo.getUserId(), userInfo.getProtocolUsername());
        }

        /**
         * Marks the entry as caused by the given user.
         *
         * @param userId   the ID of the user
         * @param userName the name of the user
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder causedByUser(@Nullable String userId, @Nullable String userName) {
            entry.setCausedByUser(userId);
            entry.setCausedByUserName(userName);

            return this;
        }

        /**
         * Creates the entry for the given tenant.
         *
         * @param tenantId   the ID of the tenant
         * @param tenantName the name of the tenant
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder forTenant(@Nullable String tenantId, @Nullable String tenantName) {
            entry.setTenant(tenantId);
            entry.setTenantName(tenantName);

            return this;
        }

        /**
         * Marks this entry as {@link AuditLogEntry#HIDDEN}.
         * <p>
         * Such entries are not visible to the user but only to the system tenant.
         *
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder hideFromUser() {
            entry.setHidden(true);

            return this;
        }

        /**
         * Writes the entry into the log table.
         */
        public void log() {
            try {
                if (!enabled
                    || elastic == null
                    || !elastic.getReadyFuture().isCompleted()
                    || Sirius.isStartedAsTest()) {
                    return;
                }

                if (!canSkip()) {
                    elastic.update(entry);
                    logToSyslog();
                }
            } catch (Exception exception) {
                Exceptions.ignore(exception);
            }
        }

        private boolean canSkip() {
            if (entry.isNegative()) {
                return false;
            }

            return elastic.select(AuditLogEntry.class)
                          .eq(AuditLogEntry.DATE, entry.getDate())
                          .eq(AuditLogEntry.CAUSED_BY_USER, entry.getCausedByUser())
                          .eq(AuditLogEntry.USER, entry.getUser())
                          .eq(AuditLogEntry.TENANT, entry.getTenant())
                          .eq(AuditLogEntry.IP, entry.getIp())
                          .eq(AuditLogEntry.MESSAGE, entry.getMessage())
                          .exists();
        }

        protected void logToSyslog() {
            if (entry.isNegative()) {
                LOG.WARN("%s (%s) caused for %s (%s) of %s (%s): %s (%s) - IP: %s, Timestamp: %s",
                         entry.getCausedByUserName(),
                         entry.getCausedByUser(),
                         entry.getUserName(),
                         entry.getUser(),
                         entry.getTenantName(),
                         entry.getTenant(),
                         entry.getMessage(),
                         NLS.get(entry.getMessage(), NLS.getDefaultLanguage()),
                         entry.getIp(),
                         NLS.toUserString(entry.getTimestamp(), NLS.getDefaultLanguage()));
            } else {
                LOG.INFO("%s (%s) caused for %s (%s) of %s (%s): %s (%s) - IP: %s, Timestamp: %s",
                         entry.getCausedByUserName(),
                         entry.getCausedByUser(),
                         entry.getUserName(),
                         entry.getUser(),
                         entry.getTenantName(),
                         entry.getTenant(),
                         entry.getMessage(),
                         NLS.get(entry.getMessage(), NLS.getDefaultLanguage()),
                         entry.getIp(),
                         NLS.toUserString(entry.getTimestamp(), NLS.getDefaultLanguage()));
            }
        }
    }

    /**
     * Prepares a "neutral" entry to be inserted into the log.
     * <p>
     * Note that a builder is returned and eventually {@link AuditLogBuilder#log()} has to be called.
     *
     * @param messageKey the message key (i18n key) to log
     * @return the builder used to supply further info
     */
    @CheckReturnValue
    public AuditLogBuilder neutral(String messageKey) {
        return new AuditLogBuilder(messageKey, false);
    }

    /**
     * Prepares a "negative" entry to be inserted into the log.
     * <p>
     * Note that a builder is returned and eventually {@link AuditLogBuilder#log()} has to be called.
     *
     * @param messageKey the message key (i18n key) to log
     * @return the builder used to supply further info
     */
    @CheckReturnValue
    public AuditLogBuilder negative(String messageKey) {
        return new AuditLogBuilder(messageKey, true);
    }
}
