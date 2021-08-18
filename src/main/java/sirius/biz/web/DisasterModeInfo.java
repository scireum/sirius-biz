/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.SAMLController;
import sirius.db.redis.Redis;
import sirius.kernel.async.CallContext;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.cache.InlineCache;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.transformers.AutoTransform;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.http.WebContext;
import sirius.web.security.MaintenanceInfo;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Represents a {@link MaintenanceInfo} for the backend ({@link ScopeInfo#DEFAULT_SCOPE}.
 * <p>
 * This mode (called the "disaster mode") can show a warning (for planned maintenance) and also
 * lock the whole space so that only administrators can login and use the backend. Everyone else
 * is presented with a "system is locked" message.
 * <p>
 * To better plan ahead, the maintenance message and the effective lock can be controlled by
 * a timestamp.
 * <p>
 * If {@link Redis} is configured and available, the settings will be shared across the whole cluster
 * and will also be retained across restarts. Otherwise the settings are only stored in memory and
 * will be lost during a restart.
 * <p>
 * Note that any change to the settings in here, might be delayed up to 10 seconds as an internal
 * cache is used to reduce the computational load.
 */
@AutoTransform(source = ScopeInfo.class, target = MaintenanceInfo.class, priority = 200)
public class DisasterModeInfo implements MaintenanceInfo {

    private static final String REDIS_DISASTER_MESSAGE_START = "disaster-message-start";
    private static final String REDIS_DISASTER_PREVIEW_MESSAGE = "disaster-preview-message";
    private static final String REDIS_DISASTER_LOCKED = "disaster-locked";
    private static final String REDIS_DISASTER_LOCK_START = "disaster-lock-start";
    private static final String REDIS_DISASTER_LOCK_MESSAGE = "disaster-lock-message";

    @Part
    private static Redis redis;

    /**
     * Contains the timestamp when to start showing the maintenance message.
     * <p>
     * If left empty, this will be shown immediately.
     */
    private LocalDateTime displayMessageStartTime;

    /**
     * Contains the maintenance message.
     */
    private String maintenancePreviewMessage;

    /**
     * Determines if the backend should also be locked for non-admin users.
     */
    private boolean lockScope;

    /**
     * Contains the timestamp when to enable the lock.
     * <p>
     * If left empty, this will be immediately.
     */
    private LocalDateTime lockStartTime;

    /**
     * Contains the message to show while the backend is locked.
     * <p>
     * If left empty, the maintenance message will be used.
     */
    private String maintenanceLockMessage;

    /**
     * Caches the effective lock flag for ten seconds to avoid frequent recomputations.
     */
    private final InlineCache<Boolean> locked =
            CacheManager.createInlineCache(Duration.ofSeconds(10), this::determineIfLocked);

    /**
     * Caches the effective message for ten seconds to avoid frequent re-computations.
     */
    private final InlineCache<String> disasterMessage =
            CacheManager.createInlineCache(Duration.ofSeconds(10), this::fetchDisasterMessage);

    /**
     * Creates a new instance if the given scope is the backend ({@link ScopeInfo#DEFAULT_SCOPE}).
     *
     * @param scopeInfo the scope to transform
     * @throws IllegalArgumentException for all other scopes so that the synthesized transformer returns
     *                                  <tt>null</tt>
     */
    public DisasterModeInfo(ScopeInfo scopeInfo) {
        if (!Strings.areEqual(ScopeInfo.DEFAULT_SCOPE.getScopeId(), scopeInfo.getScopeId())) {
            throw new IllegalArgumentException();
        }
        scopeInfo.attach(DisasterModeInfo.class, this);
    }

    private void updateFromRedis() {
        redis.exec(() -> "Update local disaster infos", db -> {
            try {
                displayMessageStartTime =
                        NLS.parseMachineString(LocalDateTime.class, db.get(REDIS_DISASTER_MESSAGE_START));
                maintenancePreviewMessage = db.get(REDIS_DISASTER_PREVIEW_MESSAGE);
                lockScope = Value.of(db.get(REDIS_DISASTER_LOCKED)).asBoolean();
                lockStartTime = NLS.parseMachineString(LocalDateTime.class, db.get(REDIS_DISASTER_LOCK_START));
                maintenanceLockMessage = db.get(REDIS_DISASTER_LOCK_MESSAGE);
            } catch (Exception e) {
                Exceptions.handle()
                          .to(Log.SYSTEM)
                          .error(e)
                          .withSystemErrorMessage("Failed to parse disaster mode settings from redis: %s (%s)")
                          .handle();
            }
        });
    }

    private boolean determineIfLocked() {
        if (redis.isConfigured()) {
            updateFromRedis();
        }

        if (!lockScope) {
            return false;
        }

        if (Strings.isEmpty(maintenanceLockMessage) && Strings.isEmpty(maintenancePreviewMessage)) {
            return false;
        }

        return isReached(lockStartTime);
    }

    private String fetchDisasterMessage() {
        if (redis.isConfigured()) {
            updateFromRedis();
        }

        if (Strings.isFilled(maintenanceLockMessage) && Boolean.TRUE.equals(locked.get())) {
            return maintenanceLockMessage;
        }

        if (Strings.isFilled(maintenancePreviewMessage) && isReached(displayMessageStartTime)) {
            return maintenancePreviewMessage;
        }

        return null;
    }

    private boolean isReached(LocalDateTime timestamp) {
        return timestamp == null || LocalDateTime.now().isAfter(timestamp);
    }

    @Override
    public boolean isLocked() {
        WebContext webContext = CallContext.getCurrent().get(WebContext.class);
        if (webContext.getRequest() != null && isWhitelistedURI(webContext.getRequestedURI())) {
            return false;
        }

        return !isAdmin() && locked.get();
    }

    /**
     * Determines if the current URI / request should skip locking.
     * <p>
     * The uris used to enable and disable locking must always be accessible so that an
     * administrator can log-in and disable locking.
     *
     * @param uri the currently requested URI
     * @return <tt>true</tt> if the uri can be accessed in spite of an active lock, <tt>false</tt> otherwise
     */
    private boolean isWhitelistedURI(String uri) {
        return DisasterController.URI_DISASTER.equals(uri)
               || DisasterController.URI_SYSTEM_DISASTER.equals(uri)
               || uri.startsWith(SAMLController.SAML_URI_PREFIX);
    }

    /**
     * Determines if the current user is an administrator.
     * <p>
     * An administrator can always perform a login (especially in order to disable the locking).
     *
     * @return <tt>true</tt> if the current user is a system administrator (even if disguised as normal user).
     */
    private boolean isAdmin() {
        return UserContext.getCurrentUser().hasPermission(DisasterController.PERMISSION_CONTROL_DISASTER_MODE);
    }

    @Nullable
    @Override
    public Message maintenanceMessage() {
        String message = disasterMessage.get();
        if (Strings.isEmpty(message)) {
            return null;
        }

        return Message.warn().withTextMessage(message);
    }

    /**
     * Updates the current maintenance / disaster settings.
     * <p>
     * Note that this also accepts inconsistent settings so that a maintenance window can be partially planned.
     *
     * @param lock                    determines if the scope should be locked
     * @param lockStartTime           determines the beginning of the lock
     * @param displayMessageStartTime determine when to start displaying the maintenance message
     * @param previewMessage          contains the maintenance message
     * @param lockMessage             contains the message to show as the system is locked
     */
    public void updateMode(boolean lock,
                           @Nullable LocalDateTime lockStartTime,
                           @Nullable LocalDateTime displayMessageStartTime,
                           @Nullable String previewMessage,
                           @Nullable String lockMessage) {

        if (redis.isConfigured()) {
            updateToRedis(lock, lockStartTime, displayMessageStartTime, previewMessage, lockMessage);
        }

        // Also update locally, especially as the DisasterController will read back
        // the value from there to render the UI...
        this.lockScope = lock;
        this.lockStartTime = lockStartTime;
        this.displayMessageStartTime = displayMessageStartTime;
        this.maintenancePreviewMessage = previewMessage;
        this.maintenanceLockMessage = lockMessage;
        this.locked.flush();
        this.disasterMessage.flush();
    }

    private void updateToRedis(boolean lock,
                               LocalDateTime lockStartTime,
                               LocalDateTime displayMessageStartTime,
                               String previewMessage,
                               String lockMessage) {
        redis.exec(() -> "Write new disaster infos", db -> {
            try {
                db.set(REDIS_DISASTER_MESSAGE_START, NLS.toMachineString(displayMessageStartTime));
                db.set(REDIS_DISASTER_PREVIEW_MESSAGE, previewMessage);
                db.set(REDIS_DISASTER_LOCKED, NLS.toMachineString(lock));
                db.set(REDIS_DISASTER_LOCK_START, NLS.toMachineString(lockStartTime));
                db.set(REDIS_DISASTER_LOCK_MESSAGE, lockMessage);
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(Log.SYSTEM)
                                .error(e)
                                .withSystemErrorMessage("Failed to write the diaster mode settings to redis: %s (%s)")
                                .handle();
            }
        });
    }

    public LocalDate getRawDisplayMessageStartDate() {
        return displayMessageStartTime == null ? null : displayMessageStartTime.toLocalDate();
    }

    public LocalTime getRawDisplayMessageStartTime() {
        return displayMessageStartTime == null ? null : displayMessageStartTime.toLocalTime();
    }

    public String getRawPreviewMessage() {
        return maintenancePreviewMessage;
    }

    public boolean isRawLocked() {
        return lockScope;
    }

    public LocalDate getRawLockStartDate() {
        return lockStartTime == null ? null : lockStartTime.toLocalDate();
    }

    public LocalTime getRawLockStartTime() {
        return lockStartTime == null ? null : lockStartTime.toLocalTime();
    }

    public String getRawLockMessage() {
        return maintenanceLockMessage;
    }
}
