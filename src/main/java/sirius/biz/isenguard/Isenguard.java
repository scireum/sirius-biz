/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.protocol.AuditLog;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.web.controller.Controller;
import sirius.web.http.Firewall;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Provides a {@link Firewall} and rate-limiting facility.
 * <p>
 * Most probably this will be based on {@link RedisLimiter redis} but other approaches can be taken by providing a
 * custom {@link Limiter} via the system config value <tt>isenguard.limiter</tt>.
 */
@Register(classes = Isenguard.class)
public class Isenguard {

    /**
     * Contains the framework name which controls if isenguard is active or not.
     */
    public static final String FRAMEWORK_ISENGUARD = "biz.isenguard";

    /**
     * Contains the permission which is required to view the rate limits applied to the current tenant.
     */
    public static final String PERMISSION_VIEW_RATE_LIMITS = "permission-view-rate-limits";

    /**
     * Signals that the limit as given in the system configuration should be used.
     * <p>
     * This is used by {@link #registerCallAndCheckRateLimitReached(String, String, int, Supplier)} and
     * {@link #registerCallAndCheckRateLimitReached(String, String, int, Runnable, Supplier)}.
     */
    public static final int USE_LIMIT_FROM_CONFIG = 0;

    /**
     * Contains the log used for all firewall-specific events.
     */
    public static final Log LOG = Log.get("isenguard");

    /**
     * Represents the type used for realms limited by IP.
     */
    public static final String REALM_TYPE_IP = "ip";

    /**
     * Represents the type used for realms limited by the tenant (id).
     */
    public static final String REALM_TYPE_TENANT = "tenant";

    /**
     * Represents the type used for realms limited by the user (id).
     */
    public static final String REALM_TYPE_USER = "user";

    private final Map<String, Limit> limits = new ConcurrentHashMap<>();

    @Part(configPath = "isenguard.limiter")
    private Limiter limiter;

    @Part
    private AuditLog auditLog;

    @Part
    private EventRecorder events;

    /**
     * Determines if the given {@code ipAddress} has already been blocked via {@link #blockIP(String)}.
     *
     * @param ipAddress the IP address to check
     * @return <tt>true</tt> if the address has been blocked, <tt>false</tt> otherwise
     */
    public boolean isIPBlacklisted(String ipAddress) {
        try {
            return limiter != null && limiter.isIPBlacklisted(ipAddress);
        } catch (Exception exception) {
            // In case of an error e.g. Redis might not be available,
            // we resort to ignoring any checks and let the application run.
            // The other option would be to block everything and essentially
            // shut down the application, which isn't feasible either...
            Exceptions.handle(LOG, exception);
            return false;
        }
    }

    /**
     * Blocks the given IP for a certain amount of time.
     *
     * @param ipAddress the IP address to block
     */
    public void blockIP(String ipAddress) {
        LOG.WARN("The IP %s was added to the list of blocked IP addresses.", ipAddress);
        limiter.block(ipAddress);

        events.record(new IPBlockedEvent().withIp(ipAddress));
    }

    /**
     * Removes the given IP from the block list.
     *
     * @param ipAddress the IP address to unblock
     */
    public void unblockIP(String ipAddress) {
        LOG.WARN("The IP %s was removed from the list of blocked IP addresses.", ipAddress);
        limiter.unblock(ipAddress);
    }

    /**
     * Enforces the rate limit for the given scope and realm by throwing an exception once the rate limit is hit.
     *
     * @param scope        the key which is used for grouping multiple events - e.g. the ip of the caller
     * @param realm        the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param infoSupplier a supplier which is invoked to provide additional incident data once the rate limit is first hit
     */
    public void enforceRateLimiting(String scope, String realm, Supplier<RateLimitingInfo> infoSupplier) {
        enforceRateLimiting(scope, realm, USE_LIMIT_FROM_CONFIG, infoSupplier);
    }

    /**
     * Enforces the rate limit for the given scope and realm by throwing an exception once the rate limit is hit.
     *
     * @param scope         the key which is used for grouping multiple events - e.g. the ip of the caller
     * @param realm         the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @param infoSupplier  a supplier which is invoked to provide additional incident data once the rate limit is first hit
     */
    public void enforceRateLimiting(String scope,
                                    String realm,
                                    int explicitLimit,
                                    Supplier<RateLimitingInfo> infoSupplier) {
        if (registerCallAndCheckRateLimitReached(scope, realm, explicitLimit, infoSupplier)) {
            throw createException(realm, explicitLimit);
        }
    }

    /**
     * Creates an exception which indicates that the rate limit for the given realm is reached.
     *
     * @param realm the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @return an exception which indicates that the rate limit for the given realm is reached
     */
    public HandledException createException(String realm) {
        return createException(realm, USE_LIMIT_FROM_CONFIG);
    }

    /**
     * Creates an exception which indicates that the rate limit for the given realm is reached.
     *
     * @param realm         the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @return an exception which indicates that the rate limit for the given realm is reached
     */
    public HandledException createException(String realm, int explicitLimit) {
        return Exceptions.createHandled()
                         .withSystemErrorMessage("Rate Limit reached: %s (%s)",
                                                 realm,
                                                 fetchLimit(realm, explicitLimit).format())
                         .hint(Controller.HTTP_STATUS, HttpResponseStatus.TOO_MANY_REQUESTS.code())
                         .handle();
    }

    /**
     * Registers an event and determines if the rate limit of the given realm for the given scope is reached.
     * <p>
     * Note that invoking this method counts towards the limit. Use {@link #checkRateLimitReached(String, String, int)}
     * if you only want to check the current state without counting the call.
     *
     * @param scope         the key which is used for grouping multiple events - e.g. the IP of the caller
     * @param realm         the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @param infoSupplier  a supplier which is invoked to provide additional incident data once the rate limit is first hit
     * @return <tt>true</tt> if the rate limit for the given scope, realm and check interval is reached,
     * <tt>false</tt> otherwise. Note, that once the limit was reached, an {@link AuditLog audit log entry} will be
     * created.
     */
    public boolean registerCallAndCheckRateLimitReached(String scope,
                                                        String realm,
                                                        int explicitLimit,
                                                        Supplier<RateLimitingInfo> infoSupplier) {
        return registerCallAndCheckRateLimitReached(scope, realm, explicitLimit, null, infoSupplier);
    }

    /**
     * Registers an event and determines if the rate limit of the given realm for the given scope is reached.
     * <p>
     * Note that invoking this method counts towards the limit. Use {@link #checkRateLimitReached(String, String, int)}
     * if you only want to check the current state without counting the call.
     *
     * @param scope            the key which is used for grouping multiple events - e.g. the IP of the caller
     * @param realm            the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit    the explicit limit which overwrites the limit given in the config.
     *                         Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @param limitReachedOnce specifies an action which is executed once the limit was reached, but then skipped for
     *                         this scope, realm and check interval.
     * @param infoSupplier     a supplier which is invoked to provide additional incident data once the rate limit is first hit
     * @return <tt>true</tt> if the rate limit for the given scope, realm and check interval is reached,
     * <tt>false</tt> otherwise. Note, that once the limit was reached, an {@link AuditLog audit log entry} will be
     * created.
     */
    public boolean registerCallAndCheckRateLimitReached(String scope,
                                                        String realm,
                                                        int explicitLimit,
                                                        Runnable limitReachedOnce,
                                                        Supplier<RateLimitingInfo> infoSupplier) {
        try {
            Limit limit = fetchLimit(realm, explicitLimit);
            if (!limit.isValid()) {
                return false;
            }

            return registerCallAndCheckLimit(scope, realm, limit.maxCalls, limit.intervalSeconds, () -> {
                handleLimitReached(scope, realm, limit, infoSupplier.get());

                if (limitReachedOnce != null) {
                    limitReachedOnce.run();
                }
            });
        } catch (Exception exception) {
            // In case of an error e.g. Redis might not be available,
            // we resort to ignoring any limits and let the application run.
            // The other option would be to block everything and essentially
            // shut down the application, which isn't feasible either...
            Exceptions.handle(LOG, exception);
            return false;
        }
    }

    /**
     * Determines if the rate limit of the given realm for the given scope is reached. This check does not have side
     * effects.
     *
     * @param scope the key which is used for grouping multiple events - e.g. the IP of the caller
     * @param realm the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @return <tt>true</tt> if the rate limit for the given scope, realm and check interval is reached,
     * <tt>false</tt> otherwise
     */
    public boolean checkRateLimitReached(String scope, String realm) {
        return checkRateLimitReached(scope, realm, USE_LIMIT_FROM_CONFIG);
    }

    /**
     * Determines if the rate limit of the given realm for the given scope is reached. This check does not have side
     * effects.
     *
     * @param scope         the key which is used for grouping multiple events - e.g. the IP of the caller
     * @param realm         the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @return <tt>true</tt> if the rate limit for the given scope, realm and check interval is reached,
     * <tt>false</tt> otherwise
     */
    public boolean checkRateLimitReached(String scope, String realm, int explicitLimit) {
        try {
            Limit limit = fetchLimit(realm, explicitLimit);
            if (!limit.isValid()) {
                return false;
            }

            return checkLimit(scope, realm, limit.intervalSeconds, limit.maxCalls);
        } catch (Exception exception) {
            // In case of an error e.g. Redis might not be available,
            // we resort to ignoring any limits and let the application run.
            // The other option would be to block everything and essentially
            // shut down the application, which isn't feasible either...
            Exceptions.handle(LOG, exception);
            return false;
        }
    }

    private Limit fetchLimit(String realm, int explicitLimit) {
        Limit limitSetting = limits.computeIfAbsent(realm, this::loadLimit);
        return explicitLimit == USE_LIMIT_FROM_CONFIG ?
               limitSetting :
               new Limit(explicitLimit, limitSetting.intervalSeconds);
    }

    private Limit loadLimit(String realm) {
        Extension setting = Sirius.getSettings().getExtension("isenguard.limit", realm);
        return new Limit(setting.getInt("limit"), (int) (setting.getMilliseconds("interval") / 1000));
    }

    private void handleLimitReached(String scope, String realm, Limit limit, RateLimitingInfo info) {
        LOG.WARN("Scope %s reached its rate-limit (%s) for realm '%s'. IP: %s, Tenant: %s, Location: %s",
                 scope,
                 limit.format(),
                 realm,
                 Value.of(info.getIp()).asString("-"),
                 Value.of(info.getTenantId()).asString("-"),
                 Value.of(info.getLocation()).asString("-"));

        auditLog.negative("Isenguard.limitReached").causedByCurrentUser().log();

        events.record(new RateLimitingTriggeredEvent().withRealm(realm)
                                                      .withScope(scope)
                                                      .withLimit(limit.maxCalls)
                                                      .withInterval(limit.intervalSeconds)
                                                      .withIp(info.getIp())
                                                      .withTenant(info.getTenantId())
                                                      .withLocation(Strings.limit(info.getLocation(), 255)));
    }

    private boolean checkLimit(String scope, String realm, int intervalInSeconds, int limit) {
        String key = computeRateLimitingKey(scope, realm, intervalInSeconds);
        return limiter.readCallCount(key) >= limit;
    }

    private boolean registerCallAndCheckLimit(String scope,
                                              String realm,
                                              int intervalInSeconds,
                                              int limit,
                                              Runnable limitReachedOnce) {
        String key = computeRateLimitingKey(scope, realm, intervalInSeconds);
        return limiter.registerCallAndCheckLimit(key, intervalInSeconds, limit, limitReachedOnce);
    }

    private String computeRateLimitingKey(String scope, String realm, int intervalInSeconds) {
        long currentInterval = (System.currentTimeMillis() / 1000) / intervalInSeconds;
        return scope + "-" + realm + "-" + currentInterval;
    }

    /**
     * Provides a string representation of the limit applied to the given scope and realm.
     * <p>
     * This will contain the current value as well as the limit and check interval.
     *
     * @param scope         the key which is used for grouping multiple events - e.g. the ip of the caller
     * @param realm         the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} or <tt>null</tt> if no explicit limit is set
     * @return a string representation of the current value and the limit which will be enforced or <tt>null</tt>
     * if no limit is applied
     */
    @Nullable
    public String getRateLimitInfo(String scope, String realm, Integer explicitLimit) {
        Limit limit = fetchLimit(realm, explicitLimit == null ? USE_LIMIT_FROM_CONFIG : explicitLimit);
        if (!limit.isValid()) {
            return null;
        }

        String key = computeRateLimitingKey(scope, realm, limit.intervalSeconds);
        int currentValue = limiter.readCallCount(key);
        return limit.format(currentValue);
    }

    /**
     * Lists all known realms with the given type.
     * <p>
     * Note that there a some common types:
     * <ul>
     * <li><b>ip</b>: A realm which is limited by the calling IP</li>
     * <li><b>tenant</b>: A realm which is limited by the calling tenant id</li>
     * <li><b>user</b>: A realm which is limited by the calling user id</li>
     * </ul>
     *
     * @param type the type to search by
     * @return a list of all realm which have the given type (specified in the system configuration)
     * @see #REALM_TYPE_IP
     * @see #REALM_TYPE_TENANT
     * @see #REALM_TYPE_USER
     */
    public List<String> getRealmsByType(String type) {
        return Sirius.getSettings()
                     .getExtensions("isenguard.limit")
                     .stream()
                     .filter(ext -> Strings.areEqual(type, ext.getString("type")))
                     .map(Extension::getId)
                     .toList();
    }

    /**
     * Returns a list of currently blocked IPs.
     * <p>
     * For efficiency reasons, this list might be limited to the latest matches (e.g. the top 50).
     *
     * @return a set of currently blocked IPs
     */
    public Set<String> getBlockedIPs() {
        return limiter.getBlockedIPs();
    }

    /**
     * Represents the limit for a specific realm, containing the maximum number of calls and the check interval in seconds.
     *
     * @param maxCalls        the maximum number of calls within the interval period
     * @param intervalSeconds the check interval in seconds during which the max calls are counted
     */
    private record Limit(int maxCalls, int intervalSeconds) {
        private boolean isValid() {
            return maxCalls > 0 && intervalSeconds > 0;
        }

        private String format() {
            return Strings.apply("%s calls within %ss", maxCalls, intervalSeconds);
        }

        private String format(int currentValue) {
            return Strings.apply("%s / %s (per %ss)", currentValue, maxCalls, intervalSeconds);
        }
    }
}
