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

    /**
     * Reserves the given response and assertion IDs until the given replay deadline.
     *
     * @param responseId          the ID of the SAML response to reserve
     * @param assertionId         the ID of the SAML assertion to reserve
     * @param replayCacheDeadline the timestamp until which the IDs must remain blocked
     * @return <tt>true</tt> if all given IDs were reserved, <tt>false</tt> if any ID was already known
     */
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

    /**
     * Attempts to reserve a single Redis key atomically with its TTL.
     *
     * @param db         the Redis connection to use
     * @param key        the key to reserve
     * @param ttlSeconds the number of seconds until the reservation expires
     * @return <tt>true</tt> if the key was created, <tt>false</tt> if it already existed
     */
    private boolean reserveKey(Jedis db, String key, long ttlSeconds) {
        String result = db.set(key, VALUE, SetParams.setParams().nx().ex(ttlSeconds));
        return REDIS_RESPONSE_SUCCESS.equals(result);
    }

    /**
     * Builds the Redis key for the given replay reservation.
     *
     * @param type the reservation type, for example <tt>response</tt> or <tt>assertion</tt>
     * @param id   the SAML ID to reserve
     * @return the Redis key used for the reservation
     */
    private String buildKey(String type, String id) {
        return KEY_PREFIX + type + ":" + id;
    }

    /**
     * Determines the Redis TTL to use for a replay reservation.
     *
     * @param replayCacheDeadline the timestamp until which the reservation must be retained
     * @return the reservation TTL in seconds, never less than one second
     */
    private long determineTTLSeconds(Instant replayCacheDeadline) {
        return Math.max(1, Duration.between(Instant.now(), replayCacheDeadline).getSeconds());
    }
}
