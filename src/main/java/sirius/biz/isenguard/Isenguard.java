/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.protocol.AuditLog;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.web.http.Firewall;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
     * This is used by {@link #isRateLimitReached(String, String, int, Supplier)} and
     * {@link #isRateLimitReached(String, String, int, Runnable, Supplier)}.
     */
    public static final int USE_LIMIT_FROM_CONFIG = 0;

    /**
     * Contains the logged used for all firewall sepcific events.
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

    private Map<String, Tuple<Integer, Integer>> limits = new ConcurrentHashMap<>();

    @Part(configPath = "isenguard.limiter")
    private Limiter limiter;

    @Part
    private AuditLog auditLog;

    @Part
    private EventRecorder events;

    /**
     * Determins if the given ipAddress has already been blocked via {@link #blockIP(String)}.
     *
     * @param ipAddress the ip address to check
     * @return <tt>true</tt> if the address has been blocked, <tt>false</tt> otherwise
     */
    public boolean isIPBlacklisted(String ipAddress) {
        try {
            return limiter != null && limiter.isIPBLacklisted(ipAddress);
        } catch (Exception e) {
            // In case of an error e.g. Redis might not be available,
            // we resort to ignoring any checks and let the application run.
            // The other option would be to block everything and essentially
            // shut down the application, which isn't feasible either...
            Exceptions.handle(LOG, e);
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

        IPBlockedEvent event = new IPBlockedEvent();
        event.setIp(ipAddress);
        events.record(event);
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
        if (isRateLimitReached(scope, realm, explicitLimit, infoSupplier)) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Rate Limit reached: %s (%s)",
                                                    realm,
                                                    formatLimit(fetchLimit(realm, explicitLimit)))
                            .handle();
        }
    }

    /**
     * Determines if the rate limit of the given realm for the given scope is reached.
     *
     * @param scope         the key which is used for grouping multiple events - e.g. the ip of the caller
     * @param realm         the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @param infoSupplier  a supplier which is invoked to provide additional incident data once the rate limit is first hit
     * @return <tt>true</tt> if the rate limit for the given ip, realm and check interval is reached,
     * <tt>false</tt> otherwise. Note, that once the limit was reached, an {@link AuditLog audit log entry} will be
     * created.
     */
    public boolean isRateLimitReached(String scope,
                                      String realm,
                                      int explicitLimit,
                                      Supplier<RateLimitingInfo> infoSupplier) {
        return isRateLimitReached(scope, realm, explicitLimit, null, infoSupplier);
    }

    private String formatLimit(Tuple<Integer, Integer> limit) {
        return Strings.apply("%s calls within %ss", limit.getFirst(), limit.getSecond());
    }

    /**
     * Determines if the rate limit of the given realm for the given IP is reached.
     *
     * @param scope            the key which is used for grouping multiple events - e.g. the ip of the caller
     * @param realm            the realm which defines the limit and check interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit    the explicit limit which overwrites the limit given in the config.
     *                         Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @param limitReachedOnce specifies an action which is executed once the limit was reached, but then skipped for
     *                         this scope, relam and check interval.
     * @param infoSupplier     a supplier which is invoked to provide additional incident data once the rate limit is first hit
     * @return <tt>true</tt> if the rate limit for the given ip, realm and check interval is reached,
     * <tt>false</tt> otherwise.
     */
    public boolean isRateLimitReached(String scope,
                                      String realm,
                                      int explicitLimit,
                                      Runnable limitReachedOnce,
                                      Supplier<RateLimitingInfo> infoSupplier) {
        try {
            Tuple<Integer, Integer> limit = fetchLimit(realm, explicitLimit);

            if (limit.getFirst() == 0 || limit.getSecond() == 0) {
                return false;
            }

            return isRateLimitReached(scope, realm, limit.getSecond(), limit.getFirst(), () -> {
                handleLimitReached(scope, realm, limit, infoSupplier.get());

                if (limitReachedOnce != null) {
                    limitReachedOnce.run();
                }
            });
        } catch (Exception e) {
            // In case of an error e.g. Redis might not be available,
            // we resort to ignoring any limits and let the application run.
            // The other option would be to block everything and essentially
            // shut down the application, which isn't feasible either...
            Exceptions.handle(LOG, e);
            return false;
        }
    }

    private Tuple<Integer, Integer> fetchLimit(String realm, int explicitLimit) {
        Tuple<Integer, Integer> limitSetting = limits.computeIfAbsent(realm, this::loadLimit);
        return explicitLimit == USE_LIMIT_FROM_CONFIG ?
               limitSetting :
               Tuple.create(explicitLimit, limitSetting.getSecond());
    }

    private Tuple<Integer, Integer> loadLimit(String realm) {
        Extension setting = Sirius.getSettings().getExtension("isenguard.limit", realm);
        return Tuple.create(setting.getInt("limit"), (int) (setting.getMilliseconds("interval") / 1000));
    }

    private void handleLimitReached(String scope, String realm, Tuple<Integer, Integer> limit, RateLimitingInfo info) {
        LOG.WARN("Scope %s reached its rate-limit (%s) for realm '%s'. IP: %s, Tenant: %s, Location: %s",
                 scope,
                 formatLimit(limit),
                 realm,
                 Value.of(info.getIp()).asString("-"),
                 Value.of(info.getTenantId()).asString("-"),
                 Value.of(info.getLocation()).asString("-"));

        auditLog.negative("Isenguard.limitReached").causedByCurrentUser().log();

        RateLimitingTriggeredEvent limitEvent = new RateLimitingTriggeredEvent();
        limitEvent.setRealm(realm);
        limitEvent.setScope(scope);
        limitEvent.setLimit(limit.getFirst());
        limitEvent.setInterval(limit.getSecond());
        limitEvent.setIp(info.getIp());
        limitEvent.setTenant(info.getTenantId());
        limitEvent.setLocation(Strings.limit(info.getLocation(), 255));
        events.record(limitEvent);
    }

    private boolean isRateLimitReached(String scope,
                                       String realm,
                                       int intervalInSeconds,
                                       int limit,
                                       Runnable limitReachedOnce) {
        String key = computeRateLimitingKey(scope, realm, intervalInSeconds);
        return limiter.increaseAndCheckLimit(key, intervalInSeconds, limit, limitReachedOnce);
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
        Tuple<Integer, Integer> limit =
                fetchLimit(realm, explicitLimit == null ? USE_LIMIT_FROM_CONFIG : explicitLimit);
        if (limit.getFirst() == 0 || limit.getSecond() == 0) {
            return null;
        }

        String key = computeRateLimitingKey(scope, realm, limit.getSecond());
        int currentValue = limiter.readLimit(key);
        return Strings.apply("%s / %s (per %ss)", currentValue, limit.getFirst(), limit.getSecond());
    }

    /**
     * Lists all known relams with the given type.
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
                     .collect(Collectors.toList());
    }

    /**
     * Returns a list of currently blocked IPs.
     * <p>
     * For efficiency reasons, this list might be limited to the latest matches (e.g. the top 50).
     *
     * @return a list of currently blocked IPs
     */
    public Set<String> getBlockedIPs() {
        return limiter.getBlockedIPs();
    }
}

