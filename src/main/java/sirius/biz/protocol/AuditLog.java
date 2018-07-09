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
import sirius.kernel.async.CallContext;
import sirius.kernel.di.Initializable;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.time.LocalDateTime;

/**
 * Inserts entries into the security log of the system.
 */
@Register(classes = {AuditLog.class, Initializable.class})
public class AuditLog implements Initializable {

    @Part
    private Elastic elastic;

    private boolean enabled;

    @Override
    public void initialize() throws Exception {
        enabled = Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_PROTOCOLS);
    }

    /**
     * Fluent builder for audit logs.
     */
    public class AuditLogBuilder {

        private AuditLogEntry entry = new AuditLogEntry();

        protected AuditLogBuilder(String message, boolean negative) {
            entry.setTimestamp(LocalDateTime.now());
            entry.setNegative(negative);
            entry.setMessage(message);
            entry.setIp(getCurrentIP());
        }

        private String getCurrentIP() {
            WebContext webContext = CallContext.getCurrent().get(WebContext.class);
            if (webContext.isValid()) {
                return webContext.getRemoteIP().toString();
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
            UserInfo user = UserContext.getCurrentUser();
            return forUser(user.getUserId(), user.getUserName()).forTenant(user.getTenantId(), user.getTenantName());
        }

        /**
         * Creates the entry for the given user.
         *
         * @param id   the ID of the user
         * @param name the name of the user
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder forUser(@Nullable String id, @Nullable String name) {
            entry.setUser(id);
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
            UserInfo user = UserContext.getCurrentUser();
            return causedByUser(user.getUserId(), user.getUserName()).forTenant(user.getTenantId(),
                                                                                user.getTenantName());
        }

        /**
         * Marks the entry as caused by the given user.
         *
         * @param id   the ID of the user
         * @param name the name of the user
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder causedByUser(@Nullable String id, @Nullable String name) {
            entry.setCausedByUser(id);
            entry.setCausedByUserName(name);

            return this;
        }

        /**
         * Creates the entry for the given tenant.
         *
         * @param id   the ID of the tenant
         * @param name the name of the tenant
         * @return the builder for fluent method calls
         */
        @CheckReturnValue
        public AuditLogBuilder forTenant(@Nullable String id, @Nullable String name) {
            entry.setTenant(id);
            entry.setTenantName(name);

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
                elastic.update(entry);
            } catch (Exception e) {
                Exceptions.ignore(e);
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
