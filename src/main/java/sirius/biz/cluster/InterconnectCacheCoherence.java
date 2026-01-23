/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.async.CallContext;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheCoherence;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Json;
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
        CacheManager.clearCoherentCacheLocally(cache.getName());
        interconnect.dispatch(getName(),
                              Json.createObject()
                                  .put(MESSAGE_TYPE, TYPE_CLEAR)
                                  .put(MESSAGE_CACHE, cache.getName())
                                  .put(MESSAGE_NODE, CallContext.getNodeName()));
    }

    @Override
    public void removeKey(Cache<String, ?> cache, String key) {
        CacheManager.removeCoherentCacheKeyLocally(cache.getName(), key);
        interconnect.dispatch(getName(),
                              Json.createObject()
                                  .put(MESSAGE_TYPE, TYPE_REMOVE)
                                  .put(MESSAGE_CACHE, cache.getName())
                                  .put(MESSAGE_KEY, key)
                                  .put(MESSAGE_NODE, CallContext.getNodeName()));
    }

    @Override
    public void removeAll(Cache<String, ?> cache, String discriminator, String testInput) {
        CacheManager.coherentCacheRemoveAllLocally(cache.getName(), discriminator, testInput);
        interconnect.dispatch(getName(),
                              Json.createObject()
                                  .put(MESSAGE_TYPE, TYPE_REMOVE_ALL)
                                  .put(MESSAGE_CACHE, cache.getName())
                                  .put(MESSAGE_DISCRIMINATOR, discriminator)
                                  .put(MESSAGE_TEST_VALUE, testInput)
                                  .put(MESSAGE_NODE, CallContext.getNodeName()));
    }

    @Override
    public void handleEvent(ObjectNode event) {
        String type = Json.tryValueString(event, MESSAGE_TYPE).orElse(null);
        String cache = Json.tryValueString(event, MESSAGE_CACHE).orElse(null);

        // Ignore our own messages, as we already have executed them...
        if (Strings.areEqual(CallContext.getNodeName(), Json.tryValueString(event, MESSAGE_NODE).orElse(null))) {
            return;
        }

        if (Strings.areEqual(type, TYPE_CLEAR)) {
            CacheManager.clearCoherentCacheLocally(cache);
        } else if (Strings.areEqual(type, TYPE_REMOVE)) {
            String key = Json.tryValueString(event, MESSAGE_KEY).orElse(null);
            CacheManager.removeCoherentCacheKeyLocally(cache, key);
        } else if (Strings.areEqual(type, TYPE_REMOVE_ALL)) {
            String discriminator = Json.tryValueString(event, MESSAGE_DISCRIMINATOR).orElse(null);
            String testValue = Json.tryValueString(event, MESSAGE_TEST_VALUE).orElse(null);
            CacheManager.coherentCacheRemoveAllLocally(cache, discriminator, testValue);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "CacheCoherence";
    }
}
