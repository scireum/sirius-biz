/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.alibaba.fastjson.JSONObject;
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

    private static final String MESSAGE_CACHE = "cache";
    private static final String MESSAGE_KEY = "key";

    @Part
    private Interconnect interconnect;

    @Override
    public void clear(Cache<String, ?> cache) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_CLEAR)
                                              .fluentPut(MESSAGE_CACHE, cache.getName()));
    }

    @Override
    public void removeKey(Cache<String, ?> cache, String key) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_REMOVE)
                                              .fluentPut(MESSAGE_CACHE, cache.getName())
                                              .fluentPut(MESSAGE_KEY, key));
    }

    @Override
    public void handleEvent(JSONObject event) {
        String type = event.getString(MESSAGE_TYPE);
        String cache = event.getString(MESSAGE_CACHE);

        if (Strings.areEqual(type, TYPE_CLEAR)) {
            CacheManager.clearCoherentCacheLocally(cache);
        } else if (Strings.areEqual(type, TYPE_REMOVE)) {
            String key = event.getString(MESSAGE_KEY);
            CacheManager.removeCoherentCacheKeyLocally(cache, key);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "CacheCoherence";
    }
}
