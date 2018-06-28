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

@Register(classes = {Isenguard.class, Firewall.class})
public class Isenguard implements Firewall {

    @Part
    private Redis redis;

    private Map<String, Tuple<Integer, Integer>> limits = new ConcurrentHashMap<>();

    @Override
    public boolean isIPBlacklisted(WebContext ctx) {
        return false;
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
        if (!redis.isConfigured()) {
            return false;
        }
        Tuple<Integer, Integer> limitSetting = limits.computeIfAbsent(realm, this::loadLimit);
        if (limitSetting.getSecond() == 0 || (limitSetting.getFirst() == 0 && limit == 0)) {
            return false;
        }

        return isRateLimitReached(ip,
                                  realm,
                                  limitSetting.getSecond(),
                                  limit <= 0 ? limitSetting.getFirst() : limitSetting.getSecond());
    }

    private Tuple<Integer, Integer> loadLimit(String realm) {
        Extension setting = Sirius.getSettings().getExtension("isenguard.limit", realm);
        return Tuple.create(setting.getInt("limit"), (int) (setting.getMilliseconds("interval") / 1000));
    }

    private boolean isRateLimitReached(String ip, String realm, int intervalInSeconds, int limit) {
        long currentInterval = (System.currentTimeMillis() / 1000) / intervalInSeconds;
        String key = "rate-limit-" + ip + "-" + realm + "-" + currentInterval;
        return redis.query(() -> "Rate Limit", db -> {
            long value = db.incr(key);
            if (value == 1) {
                db.expire(key, intervalInSeconds);
            } else if (value == limit) {
                //TODO log
            }
            return value >= limit;
        });
    }
}
//
//    -Multi-Redis
//            -Crunchlog-Records
//            -Crunchlog-Uplinks
//            -Clickhouse
//            -ES
//            -File
