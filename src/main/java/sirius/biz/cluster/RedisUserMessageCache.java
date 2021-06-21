/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sirius.db.redis.Redis;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Message;
import sirius.web.controller.MessageLevel;
import sirius.web.http.DistributedUserMessageCache;
import sirius.web.http.WebContext;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_HTML = "html";

    @Part
    private Redis redis;

    @Override
    public void put(String key, List<Message> value) {
        JSONArray array = new JSONArray();
        for (Message message : value) {
            array.add(new JSONObject().fluentPut(FIELD_HTML, message.getHtml()).fluentPut(FIELD_TYPE, message.getType()));
        }

        redis.exec(() -> "Write to RedisUserMessageCache",
                   jedis -> jedis.setex(CACHE_NAME + key, (int) DEFAULT_TTL, array.toJSONString()));
    }

    @Override
    public List<Message> getAndRemove(String key) {
        String json = redis.query(() -> "getAndRemove from RedisUserMessageCache ", jedis -> {
            String response = jedis.get(CACHE_NAME + key);
            jedis.del(CACHE_NAME + key);
            return response;
        });

        if (Strings.isEmpty(json)) {
            return Collections.emptyList();
        }

        return JSON.parseArray(json)
                   .stream()
                   .map(JSONObject.class::cast)
                   .map(object -> new Message(Value.of(object.getString(FIELD_TYPE))
                                                   .getEnum(MessageLevel.class)
                                                   .orElse(MessageLevel.INFO), object.getString(FIELD_HTML)))
                   .collect(Collectors.toList());
    }

    @Override
    public boolean isReady() {
        return redis.isConfigured();
    }
}
