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

@Register(classes = StorageUtils.class)
public class StorageUtils {

    /**
     * Names the framework which must be enabled to activate the storage feature.
     */
    public static final String FRAMEWORK_STORAGE = "biz.storage";

    public static final Log LOG = Log.get("storage");
    public static final String KEY_PHYSICAL_ENGINE = "engine";
    public static final String KEY_PHYSICAL_STORE_PER_YEAR = "storePerYear";
    public static final String KEY_PHYSICAL_PATH = "path";
    public static final String KEY_PHYSICAL_BASE_DIR = "baseDir";
    public static final String KEY_PHYSICAL_HTTP_ACCESS = "access";
    public static final String KEY_PHYSICAL_STORE = "store";
    public static final String KEY_PHYSICAL_REPLICATION_SPACE = "replicationSpace";

    public static final String SCOPE_PHYSICAL = "physical";

    @ConfigValue("storage.sharedSecret")
    private String sharedSecret;
    private String safeSharedSecret;

    public Collection<Extension> getStorageSpaces(String scope) {
        return Sirius.getSettings().getExtensions("storage."+scope+".spaces");
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

//    /**
//     * Creates a builder to construct a download URL for an object.
//     *
//     * @param bucket    the bucket containing the object
//     * @param objectKey the object to create an URL for
//     * @return a builder to construct a download URL
//     */
//    public DownloadBuilder prepareDownload(String bucket, String objectKey) {
//        return new DownloadBuilder(this, bucket, objectKey);
//    }
//
//    /**
//     * Creates a builder for download URLs based on a {@link VirtualObject} which might avoid a lookup.
//     *
//     * @param object the object to deliver
//     * @return a builder to construct a download URL
//     */
//    protected DownloadBuilder prepareDownload(VirtualObject object) {
//        return new DownloadBuilder(this, object);
//    }
//
//    /**
//     * Creates a download URL for a fully populated builder.
//     *
//     * @param downloadBuilder the builder specifying the details of the url
//     * @return a download URL for the object described by the builder
//     */
//    protected String createURL(DownloadBuilder downloadBuilder) {
//        String result = getStorageEngine(downloadBuilder.getBucket()).createURL(downloadBuilder);
//        if (result == null) {
//            result = buildURL(downloadBuilder);
//        }
//
//        return result;
//    }
//
//    /**
//     * Provides a facility to provide an internal download URL which utilizes {@link
//     * PhysicalStorageEngine#deliver(WebContext, String, String, String)}.
//     * <p>
//     * This is the default way of delivering files. However, a {@link PhysicalStorageEngine} can provide its
//     * own URLs which are handled outside of the system.
//     *
//     * @param downloadBuilder the builder specifying the details of the download
//     * @return the download URL
//     */
//    private String buildURL(DownloadBuilder downloadBuilder) {
//        StringBuilder result = new StringBuilder();
//        if (Strings.isFilled(downloadBuilder.getBaseURL())) {
//            result.append(downloadBuilder.getBaseURL());
//        }
//        result.append("/storage/physical/");
//        result.append(downloadBuilder.getBucket());
//        result.append("/");
//        if (downloadBuilder.isEternallyValid()) {
//            result.append(computeEternallyValidHash(downloadBuilder.getPhysicalKey()));
//        } else {
//            result.append(computeHash(downloadBuilder.getPhysicalKey(), 0));
//        }
//        result.append("/");
//        if (Strings.isFilled(downloadBuilder.getAddonText())) {
//            result.append(Strings.reduceCharacters(NON_URL_CHARACTERS.matcher(downloadBuilder.getAddonText())
//                                                                     .replaceAll("-")));
//            result.append("--");
//        }
//        result.append(downloadBuilder.getPhysicalKey());
//        result.append(".");
//        result.append(downloadBuilder.getFileExtension());
//        if (Strings.isFilled(downloadBuilder.getFilename())) {
//            result.append("?name=");
//            result.append(Strings.urlEncode(downloadBuilder.getFilename()));
//        }
//
//        return result.toString();
//    }

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
