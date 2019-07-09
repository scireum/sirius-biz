/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;

/**
 * Provides various helpers for the storage framework.
 * <p>
 * This provides access to the configuration for all layers and some authentication utilities.
 */
@Register(classes = StorageUtils.class)
public class StorageUtils {

    /**
     * Names the framework which must be enabled to activate the storage feature.
     */
    public static final String FRAMEWORK_STORAGE = "biz.storage";

    /**
     * Represents the central logger for the whole storage framework.
     */
    public static final Log LOG = Log.get("storage");

    /**
     * Lists the layers which are placed in the config as <tt>storage.layer1.spaces</tt> etc. Each of
     * these layers provide a list of {@link Extension extensions} - one per storage space.
     */
    public enum ConfigScope {LAYER1, LAYER2, LAYER3}

    @ConfigValue("storage.sharedSecret")
    private String sharedSecret;
    private String safeSharedSecret;

    /**
     * Returns all configured extensions / storage spaces for the given scope.
     *
     * @param scope the scope to query
     * @return the list of extensions available for this scope
     */
    public Collection<Extension> getStorageSpaces(ConfigScope scope) {
        return Sirius.getSettings().getExtensions("storage." + scope.name().toLowerCase() + ".spaces");
    }

    /**
     * Verifies the authentication hash for the given key.
     *
     * @param key  the key to verify
     * @param hash the hash to verify
     * @return <tt>true</tt> if the hash verifies the given object key, <tt>false</tt> otherwise
     */
    public boolean verifyHash(String key, String hash) {
        // Check for a hash for today...
        if (Strings.areEqual(hash, computeHash(key, 0))) {
            return true;
        }

        // Check for an eternally valid hash...
        if (Strings.areEqual(hash, computeEternallyValidHash(key))) {
            return true;
        }

        // Check for hashes up to two days of age...
        for (int i = 1; i < 3; i++) {
            if (Strings.areEqual(hash, computeHash(key, -i)) || Strings.areEqual(hash, computeHash(key, i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Computes an authentication hash for the given physical storage key and the offset in days (from the current).
     *
     * @param physicalKey the key to authenticate
     * @param offsetDays  the offset from the current day
     * @return a hash valid for the given day and key
     */
    private String computeHash(String physicalKey, int offsetDays) {
        return Hashing.md5()
                      .hashString(physicalKey + getTimestampOfDay(offsetDays) + getSharedSecret(), Charsets.UTF_8)
                      .toString();
    }

    /**
     * Computes an authentication hash which is eternally valid.
     *
     * @param physicalKey the key to authenticate
     * @return a hash valid forever
     */
    private String computeEternallyValidHash(String physicalKey) {
        return Hashing.md5().hashString(physicalKey + getSharedSecret(), Charsets.UTF_8).toString();
    }

    /**
     * Generates a timestamp for the day plus the provided day offset.
     *
     * @param day the offset from the current day
     * @return the effective timestamp (number of days since 01.01.1970) in days
     */
    private String getTimestampOfDay(int day) {
        Instant midnight = LocalDate.now().plusDays(day).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return String.valueOf(midnight.toEpochMilli());
    }

    /**
     * Determines the shared secret to use.
     *
     * @return the shared secret to use. Which is either taken from <tt>storage.sharedSecret</tt> in the system config
     * or a random value if the system is not configured properly
     */
    private String getSharedSecret() {
        if (safeSharedSecret == null) {
            if (Strings.isFilled(sharedSecret)) {
                safeSharedSecret = sharedSecret;
            } else {
                LOG.WARN("Please specify a secure and random value for 'storage.sharedSecret' in the 'instance.conf'!");
                safeSharedSecret = String.valueOf(System.currentTimeMillis());
            }
        }

        return safeSharedSecret;
    }
}
