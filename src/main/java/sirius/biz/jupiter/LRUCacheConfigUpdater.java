/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;
import sirius.kernel.settings.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Updates the config for all known LRU caches.
 * <p>
 * This essentailly reads the settings from the system config (<tt>jupiter.caches</tt> or
 * <tt>jupiter.[name].caches</tt>
 */
@Register
public class LRUCacheConfigUpdater implements JupiterConfigUpdater {

    @Override
    public void emitConfig(String instance, Extension systemConfig, Map<String, Object> config) {
        if (!systemConfig.has("caches")) {
            return;
        }

        Map<String, Object> cachesConfig = new HashMap<>();
        for (Map.Entry<String, ConfigValue> ext : systemConfig.getConfig("caches").entrySet()) {
            if (ext.getValue() instanceof ConfigObject) {
                Settings cacheSettings = new Settings(((ConfigObject) ext.getValue()).toConfig(), false);
                Map<String, Object> cacheConfig = new HashMap<>();
                cacheConfig.put("size", cacheSettings.get("size").asInt(0));
                cacheConfig.put("max_memory", cacheSettings.get("maxMemory").asString());
                cacheConfig.put("soft_ttl", cacheSettings.get("softTTL").asString());
                cacheConfig.put("hard_ttl", cacheSettings.get("hardTTL").asString());
                cacheConfig.put("refresh_interval", cacheSettings.get("refreshInterval").asString());
                cachesConfig.put(ext.getKey(), cacheConfig);
            }
        }

        if (!cachesConfig.isEmpty()) {
            config.put("caches", cachesConfig);
        }
    }
}
