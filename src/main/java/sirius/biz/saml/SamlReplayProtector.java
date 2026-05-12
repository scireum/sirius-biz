/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.saml;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import sirius.db.redis.Redis;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;

/**
 * Provides cluster-wide SAML replay protection by reserving response and assertion IDs in Redis.
 */
@Register(classes = SamlReplayProtector.class)
public class SamlReplayProtector {

    private static final String KEY_PREFIX = "saml:replay:";
    private static final String VALUE = "1";
    private static final String REDIS_RESPONSE_SUCCESS = "OK";

    @Part
    private Redis redis;

    boolean reserve(@Nullable String responseId, @Nullable String assertionId, Instant replayCacheDeadline) {
        if (!redis.isConfigured()) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Invalid SAML Response: Redis is required for replay protection.")
                            .handle();
        }

        String responseKey = Strings.isFilled(responseId) ? buildKey("response", responseId) : null;
        String assertionKey = Strings.isFilled(assertionId) ? buildKey("assertion", assertionId) : null;
        if (responseKey == null && assertionKey == null) {
            return true;
        }

        long ttlSeconds = determineTTLSeconds(replayCacheDeadline);
        return redis.query(() -> "Reserve SAML response for replay protection", db -> {
            boolean responseReserved = false;
            if (responseKey != null) {
                responseReserved = reserveKey(db, responseKey, ttlSeconds);
                if (!responseReserved) {
                    return false;
                }
            }

            if (assertionKey != null && !reserveKey(db, assertionKey, ttlSeconds)) {
                if (responseReserved) {
                    db.del(responseKey);
                }

                return false;
            }

            return true;
        });
    }

    private boolean reserveKey(Jedis db, String key, long ttlSeconds) {
        String result = db.set(key, VALUE, SetParams.setParams().nx().ex(ttlSeconds));
        return REDIS_RESPONSE_SUCCESS.equals(result);
    }

    private String buildKey(String type, String id) {
        return KEY_PREFIX + type + ":" + id;
    }

    private long determineTTLSeconds(Instant replayCacheDeadline) {
        return Math.max(1, Duration.between(Instant.now(), replayCacheDeadline).getSeconds());
    }
}
