/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.async.CallContext;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheCoherence;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides a {@link CacheCoherence cache coherence} handler based on the {@link Interconnect}.
 */
@Register
public class InterconnectCacheCoherence implements CacheCoherence, InterconnectHandler {

    private static final String MESSAGE_TYPE = "type";
    private static final String TYPE_CLEAR = "clear";
    private static final String TYPE_REMOVE = "remove";
    private static final String TYPE_REMOVE_ALL = "removeAll";

    private static final String MESSAGE_CACHE = "cache";
    private static final String MESSAGE_KEY = "key";
    private static final String MESSAGE_DISCRIMINATOR = "discriminator";
    private static final String MESSAGE_TEST_VALUE = "testValue";
    private static final String MESSAGE_NODE = "node";

    @Part
    private Interconnect interconnect;

    @Override
    public void clear(Cache<String, ?> cache) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_CLEAR)
                                              .fluentPut(MESSAGE_CACHE, cache.getName())
                                              .fluentPut(MESSAGE_NODE, CallContext.getNodeName()));
        CacheManager.clearCoherentCacheLocally(cache.getName());
    }

    @Override
    public void removeKey(Cache<String, ?> cache, String key) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_REMOVE)
                                              .fluentPut(MESSAGE_CACHE, cache.getName())
                                              .fluentPut(MESSAGE_KEY, key)
                                              .fluentPut(MESSAGE_NODE, CallContext.getNodeName()));
        CacheManager.removeCoherentCacheKeyLocally(cache.getName(), key);
    }

    @Override
    public void removeAll(Cache<String, ?> cache, String discriminator, String testInput) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_REMOVE_ALL)
                                              .fluentPut(MESSAGE_CACHE, cache.getName())
                                              .fluentPut(MESSAGE_DISCRIMINATOR, discriminator)
                                              .fluentPut(MESSAGE_TEST_VALUE, testInput)
                                              .fluentPut(MESSAGE_NODE, CallContext.getNodeName()));
        CacheManager.coherentCacheRemoveAllLocally(cache.getName(), discriminator, testInput);
    }

    @Override
    public void handleEvent(JSONObject event) {
        String type = event.getString(MESSAGE_TYPE);
        String cache = event.getString(MESSAGE_CACHE);

        // Ignore our own messages, as we already have executed them...
        if (Strings.areEqual(CallContext.getNodeName(), event.getString(MESSAGE_NODE))) {
            return;
        }

        if (Strings.areEqual(type, TYPE_CLEAR)) {
            CacheManager.clearCoherentCacheLocally(cache);
        } else if (Strings.areEqual(type, TYPE_REMOVE)) {
            String key = event.getString(MESSAGE_KEY);
            CacheManager.removeCoherentCacheKeyLocally(cache, key);
        } else if (Strings.areEqual(type, TYPE_REMOVE_ALL)) {
            String discriminator = event.getString(MESSAGE_DISCRIMINATOR);
            String testValue = event.getString(MESSAGE_TEST_VALUE);
            CacheManager.coherentCacheRemoveAllLocally(cache, discriminator, testValue);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "CacheCoherence";
    }
}
