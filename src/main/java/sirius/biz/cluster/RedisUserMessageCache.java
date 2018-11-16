/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.alibaba.fastjson.JSON;
import sirius.db.redis.Redis;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Message;
import sirius.web.http.DistributedUserMessageCache;
import sirius.web.http.WebContext;

import java.time.Duration;
import java.util.List;

/**
 * Provides a distributed cache to store not yet shown user messages.
 * <p>
 * This is used by {@link sirius.web.http.UserMessagesCache#cacheUserMessages(WebContext)}
 * and {@link sirius.web.http.UserMessagesCache#restoreCachedUserMessages(WebContext)} to cache
 * user messages which were not shown to a user due to a redirect.
 */
@Register
public class RedisUserMessageCache implements DistributedUserMessageCache {

    private static final String CACHE_NAME = "user-messages-";
    private static final long DEFAULT_TTL = Duration.ofMinutes(1).getSeconds();

    @Part
    private Redis redis;

    @Override
    public void put(String key, List<Message> value) {
        String jsonString = JSON.toJSONString(value);
        redis.exec(() -> "Write to RedisUserMessageCache",
                   jedis -> jedis.setex(CACHE_NAME + key, (int) DEFAULT_TTL, jsonString));
    }

    @Override
    public List<Message> getAndRemove(String key) {
        String json = redis.query(() -> "getAndRemove from RedisUserMessageCache ", jedis -> {
            String response = jedis.get(CACHE_NAME + key);
            jedis.del(CACHE_NAME + key);
            return response;
        });

        return JSON.parseArray(json, Message.class);
    }

    @Override
    public boolean isReady() {
        return redis.isConfigured();
    }
}
