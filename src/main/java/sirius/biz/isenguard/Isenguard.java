/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.protocol.AuditLog;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.web.http.Firewall;
import sirius.web.http.WebContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a {@link Firewall} and rate-limiting facility.
 * <p>
 * Most probably this will be based on {@link RedisLimiter redis} but other approaches can be taken by providing a
 * custom {@link Limiter} via the system config value <tt>isenguard.limiter</tt>.
 */
@Register(classes = {Isenguard.class, Firewall.class}, framework = Isenguard.FRAMEWORK_ISENGUARD)
public class Isenguard implements Firewall {

    /**
     * Contains the framework name which controls if isenguard is active or not.
     */
    public static final String FRAMEWORK_ISENGUARD = "biz.isenguard";

    /**
     * Signals that the limit as given in the system configuration should be used.
     * <p>
     * This is used by {@link #isRateLimitReached(String, String, int)} and
     * {@link #isRateLimitReached(String, String, int, Runnable)}.
     */
    public static final int USE_LIMIT_FROM_CONFIG = 0;

    /**
     * Contains the logged used for all firewall sepcific events.
     */
    public static final Log LOG = Log.get("isenguard");

    private Map<String, Tuple<Integer, Integer>> limits = new ConcurrentHashMap<>();

    @Part(configPath = "isenguard.limiter")
    private Limiter limiter;

    @Part
    private AuditLog auditLog;

    @Override
    public boolean isIPBlacklisted(WebContext ctx) {
        return limiter != null && limiter.isIPBLacklisted(ctx.getRemoteIP().toString());
    }

    /**
     * Blocks the given IP for a certain amount of time.
     *
     * @param ipAddress the IP address to block
     */
    public void blockIP(String ipAddress) {
        LOG.WARN("The IP %s was added to the list of blocked IP addresses.", ipAddress);
        limiter.block(ipAddress);
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

    @Override
    public boolean handleRateLimiting(WebContext ctx, String realm) {
        if (isRateLimitReached(ctx.getRemoteIP().toString(), realm, USE_LIMIT_FROM_CONFIG)) {
            ctx.respondWith().error(HttpResponseStatus.TOO_MANY_REQUESTS);
            return true;
        }

        return false;
    }

    /**
     * Determines if the rate limit of the given realm for the given IP is reached.
     *
     * @param ip            the ip which caused the event
     * @param realm         the realm which defines the limit and check-interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit the explicit limit which overwrites the limit given in the config.
     *                      Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @return <tt>true</tt> if the rate limit for the given ip, realm and check interval is reached,
     * <tt>false</tt> otherwise. Note, that once the limit was reached, an {@link AuditLog audit log entry} will be
     * created.
     */
    public boolean isRateLimitReached(String ip, String realm, int explicitLimit) {
        return isRateLimitReached(ip, realm, explicitLimit, () -> {
            LOG.WARN("IP: %s reached its rate-limit for realm '%s' and subsequent requests might be blocked...",
                     ip,
                     realm);
            auditLog.negative("Isenguard.limitReached").causedByCurrentUser().log();
        });
    }

    /**
     * Determines if the rate limit of the given realm for the given IP is reached.
     *
     * @param ip               the ip which caused the event
     * @param realm            the realm which defines the limit and check-interval (<tt>isenguard.limit.[realm]</tt>
     * @param explicitLimit    the explicit limit which overwrites the limit given in the config.
     *                         Use {@link #USE_LIMIT_FROM_CONFIG} if no explicit limit is set
     * @param limitReachedOnce specifies an action which is executed once the limit was reached, but then skipped for
     *                         this ip, relam and check interval.
     * @return <tt>true</tt> if the rate limit for the given ip, realm and check interval is reached,
     * <tt>false</tt> otherwise.
     */
    public boolean isRateLimitReached(String ip, String realm, int explicitLimit, Runnable limitReachedOnce) {
        Tuple<Integer, Integer> limitSetting = limits.computeIfAbsent(realm, this::loadLimit);
        int intervalInSeconds = limitSetting.getSecond();
        int limitFromConfig = limitSetting.getFirst();

        if (intervalInSeconds == 0 || (explicitLimit == USE_LIMIT_FROM_CONFIG && limitFromConfig == 0)) {
            return false;
        }

        return isRateLimitReached(ip,
                                  realm,
                                  intervalInSeconds,
                                  explicitLimit > 0 ? explicitLimit : limitFromConfig,
                                  limitReachedOnce);
    }

    private Tuple<Integer, Integer> loadLimit(String realm) {
        Extension setting = Sirius.getSettings().getExtension("isenguard.limit", realm);
        return Tuple.create(setting.getInt("limit"), (int) (setting.getMilliseconds("interval") / 1000));
    }

    private boolean isRateLimitReached(String ip,
                                       String realm,
                                       int intervalInSeconds,
                                       int limit,
                                       Runnable limitReachedOnce) {
        long currentInterval = (System.currentTimeMillis() / 1000) / intervalInSeconds;
        String key = ip + "-" + realm + "-" + currentInterval;

        return limiter.increaseAndCheckLimit(key, intervalInSeconds, limit, limitReachedOnce);
    }
}

