/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.db.redis.Redis;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;
import sirius.web.http.Firewall;
import sirius.web.http.WebContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Register(classes = {Isenguard.class, Firewall.class}, framework = Isenguard.FRAMEWORK_ISENGUARD)
public class Isenguard implements Firewall {

    public static final String FRAMEWORK_ISENGUARD = "biz.isenguard";

    private Map<String, Tuple<Integer, Integer>> limits = new ConcurrentHashMap<>();
    private Limiter limiter;

    @Part
    private Redis redis;

    @Override
    public boolean isIPBlacklisted(WebContext ctx) {
        return limiter != null && limiter.isIPBLacklisted(ctx.getRemoteIP().toString());
    }

    public void blockIP(String ipAddress) {
        getLimiter().block(ipAddress);
    }

    public void unblockIP(String ipAddress) {
        getLimiter().unblock(ipAddress);
    }

    public void reportNegativeEvent(String ipAddress) {
        isRateLimitReached(ipAddress, "security", 10 * 60, 10, () -> blockIP(ipAddress));
    }

    @Override
    public boolean handleRateLimiting(WebContext ctx, String realm) {
        if (isRateLimitReached(ctx.getRemoteIP().toString(), realm)) {
            ctx.respondWith().error(HttpResponseStatus.TOO_MANY_REQUESTS);
            return true;
        }

        return false;
    }

    public boolean isRateLimitReached(String ip, String realm) {
        return isRateLimitReached(ip, realm, 0);
    }

    public boolean isRateLimitReached(String ip, String realm, int limit) {
        Tuple<Integer, Integer> limitSetting = limits.computeIfAbsent(realm, this::loadLimit);
        if (limitSetting.getSecond() == 0 || (limitSetting.getFirst() == 0 && limit == 0)) {
            return false;
        }

        return isRateLimitReached(ip,
                                  realm,
                                  limitSetting.getSecond(),
                                  limit > 0 ? limit : limitSetting.getFirst(),
                                  () -> reportNegativeEvent(ip));
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
        String key = "rate-limit-" + ip + "-" + realm + "-" + currentInterval;

        return getLimiter().increaseAndCheckLimit(ip, key, intervalInSeconds, limit, limitReachedOnce);

    }

    private Limiter getLimiter() {
        if (limiter == null) {
            limiter = determineLimiter();
        }

        return limiter;
    }

    private Limiter determineLimiter() {
        return redis.isConfigured() ? new RedisLimiter(redis) : new JavaLimiter();
    }
}
// IsenGuard
//      - WebUI
//      - Local
// ObjectStores
// SearchableEntity
// DistributedLoops // Metrics // WorkQueues // Crunchlogs // Reports // Queries // Dashboards

//--> Tickets anlegen!!
//
//-> MEMOIO Release-Plan
//
//-> MEMOIO-Public-Widgets
//-> MEMOIO-Widgets abonnieren
//-> MEMOIO Kundenkarten / Kundenrelationen (Firma - Firma / Firma-Person)
//-> MEMOIO Tasks
//-> MEMOIO-Baustellenverwaltung
//-> MEMOIO-Tacks
//
//-> MEMOIO-ServiceTags
//-> MEMOIO-Dates
//-> MEMOIO-WebRTC
//-> OXOMI-Statistics
//-> OXOMI-Badges / Dashboard / Gamification
//-> OXOMI-Mongo
//-> OXOMI-Elastic
//-> OXOMI-Item-Portals
//-> OXOMI+MEMOIO
