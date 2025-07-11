/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import com.amazonaws.internal.ResettableInputStream;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import sirius.biz.protocol.TraceData;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.db.KeyGenerator;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.Mixing;
import sirius.kernel.Sirius;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides the main facility for storing and retrieving objects (files) to or from buckets.
 * <p>
 * The object storage is organized in two layers. The virtual layer is completely stored in the database and contains
 * all metadata of buckets and objects.
 * <p>
 * Everytime an object is created or updated, a <b>physical object</b> is created and stored using the {@link
 * PhysicalStorageEngine} of the bucket. Therefore a physical object key which is stored in the virtual objects
 * always contains the same data. If the data changes, the physical key changes but the virtual key remains the same.
 * <p>
 * Using this approach, the generated URLs can be cached without having the worry about this in the data model.
 * <p>
 * For database entities referencing virtual objects a {@link StoredObjectRef} can be used, which takes care of
 * referential integrity (deletes the object, if the entity is deleted etc.)
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
@Register(classes = Storage.class, framework = Storage.FRAMEWORK_STORAGE)
public class Storage {

    /**
     * Logger used by the storage facility.
     */
    public static final Log LOG = Log.get("storage");

    private static final Log DEPRECATION_LOG = Log.get("deprecated");

    /**
     * Names the framework which must be enabled to activate the storage feature.
     */
    public static final String FRAMEWORK_STORAGE = "biz.storage";

    private static final byte[] EMPTY_BUFFER = new byte[0];

    private static final Pattern NON_URL_CHARACTERS = Pattern.compile("[^a-zA-Z0-9_.]");

    private static final Cache<String, VirtualObject> virtualObjectCache =
            CacheManager.createCoherentCache("virtual-objects");

    @Part
    private OMA oma;

    @Part
    private KeyGenerator keyGen;

    @Part
    private Tasks tasks;

    @Part
    private GlobalContext context;

    @ConfigValue("storage.sharedSecret")
    @Nullable
    private String sharedSecret;
    private String safeSharedSecret;

    private Map<String, BucketInfo> buckets;

    private Map<String, BucketInfo> getBucketMap() {
        if (buckets == null) {
            buckets = loadBuckets();
        }
        return buckets;
    }

    private LinkedHashMap<String, BucketInfo> loadBuckets() {
        return Sirius.getSettings()
                     .getExtensions("storage.buckets")
                     .stream()
                     .map(BucketInfo::new)
                     .collect(Collectors.toMap(BucketInfo::getName,
                                               Function.identity(),
                                               (a, b) -> a,
                                               LinkedHashMap::new));
    }

    /**
     * Returns a list of all known buckets known to the system.
     *
     * @return a collection of all buckets known to the system (declared in <tt>storage.buckets</tt> in the system
     * config).
     */
    public Collection<BucketInfo> getBuckets() {
        return getBucketMap().values();
    }

    /**
     * Returns the bucket infos for the bucket with the given name.
     *
     * @param bucket the name of the bucket to fetch infos for
     * @return the bucket wrapped as optional or an empty optional if the bucket is unknown
     */
    public Optional<BucketInfo> getBucket(String bucket) {
        return Optional.ofNullable(getBucketMap().get(bucket));
    }

    protected PhysicalStorageEngine getStorageEngine(String bucketName) {
        return getBucket(bucketName).map(BucketInfo::getEngine)
                                    .orElseThrow(() -> Exceptions.handle()
                                                                 .withSystemErrorMessage("Unknown storage bucket: %s",
                                                                                         bucketName)
                                                                 .handle());
    }

    /**
     * Tries to find the object with the given id, for the given tenant and bucket.
     * <p>
     * Uses a cache for the {@link VirtualObject virtual objects}. When fetching an object from the cache, the buckets
     * and tennants are compared to ensure integrity. Only non-temporary objects are cached.
     *
     * @param tenant the tenant to filter on
     * @param bucket the bucket to search in
     * @param key    the globally unique id to search by
     * @return the object in the given bucket with the given key wrapped as optional or an empty optional if no such
     * object exists.
     */
    public Optional<StoredObject> findByKey(@Nullable SQLTenant tenant, String bucket, String key) {
        VirtualObject cachedObject = virtualObjectCache.get(key);

        if (cachedObject != null) {
            if (checkIntegrity(cachedObject, tenant, bucket)) {
                return Optional.of(cachedObject);
            }

            return Optional.empty();
        }

        VirtualObject virtualObject = oma.select(VirtualObject.class)
                                         .eqIgnoreNull(VirtualObject.TENANT, tenant)
                                         .eq(VirtualObject.BUCKET, bucket)
                                         .eq(VirtualObject.OBJECT_KEY, key)
                                         .queryFirst();

        if (virtualObject != null && !virtualObject.isTemporary()) {
            virtualObjectCache.put(key, virtualObject);
        }

        return Optional.ofNullable(virtualObject);
    }

    /**
     * Clears the cache for a given {@link VirtualObject}.
     *
     * @param virtualObject the virtual object
     */
    protected void clearCacheForVirtualObject(VirtualObject virtualObject) {
        virtualObjectCache.remove(virtualObject.getObjectKey());
    }

    private boolean checkIntegrity(VirtualObject virtualObject, @Nullable SQLTenant tenant, String bucket) {
        return (tenant == null || virtualObject.getTenant().is(tenant)) && virtualObject.getBucket().equals(bucket);
    }

    /**
     * Lists all objects within the given bucket for the given tenant.
     *
     * @param bucket   the bucket to search in
     * @param tenant   the tenant to filter on
     * @param iterator the iterator to process the objects
     */
    public void list(@Nullable BucketInfo bucket, @Nullable SQLTenant tenant, Predicate<StoredObject> iterator) {
        if (bucket == null || !UserContext.getCurrentUser().hasPermission(bucket.getPermission())) {
            return;
        }

        oma.select(VirtualObject.class)
           .eqIgnoreNull(VirtualObject.TENANT, tenant)
           .eq(VirtualObject.BUCKET, bucket.getName())
           .orderDesc(VirtualObject.TRACE.inner(TraceData.CHANGED_AT))
           .iterate(iterator::test);
    }

    /**
     * Normalizes the given path to be used in {@link VirtualObject#PATH}
     *
     * @param path the path to cleanup
     * @return the normalized path without \ or // or " "
     */
    public static String normalizePath(String path) {
        if (Strings.isEmpty(path)) {
            return null;
        }
        String normalizedPath = path.trim().replace(" ", "").replace("\\", "/").replaceAll("/+", "/").toLowerCase();
        if (normalizedPath.length() == 0) {
            return null;
        }
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedPath;
    }

    /**
     * Tries to find the object with the given path, for the given tenant and bucket.
     *
     * @param tenant     the tenant to filter on
     * @param bucketName the bucket to search in
     * @param path       the path used to lookup the object
     * @return the object in the given bucket with the given path wrapped as optional or an empty optional if no such
     * object exists.
     */
    public Optional<StoredObject> findByPath(SQLTenant tenant, String bucketName, String path) {
        if (shouldLogDeprecated(bucketName)) {
            Exceptions.logDeprecatedMethodUse();
        }

        String normalizedPath = normalizePath(path);

        if (normalizedPath == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(findWithNormalizedPath(tenant, bucketName, normalizedPath));
    }

    /**
     * Tries to find the object with the given path, for the given tenant and bucket. If it doesn't exist, it is
     * created.
     *
     * @param tenant     the tenant to filter on
     * @param bucketName the bucket to search in
     * @param path       the path used to lookup the object
     * @return the object in the given bucket with the given path which was either found or newly created
     */
    public StoredObject findOrCreateObjectByPath(SQLTenant tenant, String bucketName, String path) {
        String normalizedPath = normalizePath(path);

        VirtualObject result = findWithNormalizedPath(tenant, bucketName, normalizedPath);

        if (result == null) {
            result = new VirtualObject();
            result.getTenant().setValue(tenant);
            result.setBucket(bucketName);
            result.setPath(path);
            oma.update(result);
        }

        return result;
    }

    private VirtualObject findWithNormalizedPath(SQLTenant tenant, String bucketName, String normalizedPath) {
        return oma.select(VirtualObject.class)
                  .eq(VirtualObject.TENANT, tenant)
                  .eq(VirtualObject.BUCKET, bucketName)
                  .eq(VirtualObject.PATH, normalizedPath)
                  .queryFirst();
    }

    /**
     * Creates a new temporary object to be used in a {@link StoredObjectRef}.
     * <p>
     * Such objects will be invisible to the user and also be automatically deleted if the referencing entity is
     * deleted. It is made permanent as soon as the referencing entity is saved.
     *
     * @param tenant     the tenant owning the object
     * @param bucketName the bucket in which the object is placed
     * @param reference  the reference for which the object was created
     * @param path       the path of the object to be created
     * @return the newly created object which has {@link VirtualObject#TEMPORARY} set to <tt>true</tt>. Therefore the
     * referencing entity must be saved to set this flag to <tt>false</tt> via {@link StoredObjectRefProperty}.
     */
    public StoredObject createTemporaryObject(SQLTenant tenant,
                                              String bucketName,
                                              @Nullable String reference,
                                              @Nullable String path) {
        VirtualObject result = new VirtualObject();
        result.getTenant().setValue(tenant);
        result.setBucket(bucketName);
        result.setReference(reference);
        result.setTemporary(true);
        result.setPath(normalizePath(path));

        oma.update(result);
        return result;
    }

    /**
     * Deletes all automatically created objects except the given one.
     * <p>
     * Removes automatically created object ({@link #createTemporaryObject(SQLTenant, String, String, String)})
     * if the reference is updated or the referencing entity is deleted.
     *
     * @param reference         the reference (field+id of the entity) being referenced
     * @param excludedObjectKey if the reference is just changed, the new object is excluded, to just delete the old
     *                          objects
     */
    protected void deleteReferencedObjects(String reference, @Nullable String excludedObjectKey) {
        if (Strings.isEmpty(reference)) {
            return;
        }
        SmartQuery<VirtualObject> query = oma.select(VirtualObject.class).eq(VirtualObject.REFERENCE, reference);
        if (Strings.isFilled(excludedObjectKey)) {
            query.ne(VirtualObject.OBJECT_KEY, excludedObjectKey);
        }

        query.delete();
    }

    /**
     * If an object is created using {@link #createTemporaryObject(SQLTenant, String, String, String)} it is marked
     * as an temporary upload up until the referencing entity is saved.
     * <p>
     * The {@link StoredObjectRef} and its {@link StoredObjectRefProperty} will then invoke this method to make
     * the object permanently stored.
     *
     * @param reference the reference to attach to the object if it was newly created
     * @param objectKey the object to mark as permanent
     */
    protected void markAsUsed(String reference, String objectKey) {
        try {
            oma.getDatabase(Mixing.DEFAULT_REALM)
               .createQuery("UPDATE virtualobject"
                            + " SET reference=${reference}, temporary = 0"
                            + " WHERE objectKey=${objectKey}"
                            + "   AND temporary=1")
               .set("reference", reference)
               .set("objectKey", objectKey)
               .executeUpdate();
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(LOG)
                      .error(exception)
                      .withSystemErrorMessage("An error occurred, when marking the object '%s' as used: %s (%s)",
                                              objectKey)
                      .handle();
        }
    }

    /**
     * Computes a base64 representation of the md5 sum of the given file.
     *
     * @param file the file to hash
     * @return a base64 encoded string representing the md5 hash of the given file
     * @throws IOException in case of an IO error
     */
    public String calculateMd5(File file) throws IOException {
        return Base64.getEncoder().encodeToString(Files.hash(file, Hashing.md5()).asBytes());
    }

    /**
     * Updates the given file using the given data.
     *
     * @param file     the S3 file to update
     * @param data     the data to store
     * @param filename the original filename to save
     */
    public void updateFile(@Nonnull StoredObject file, @Nonnull File data, @Nullable String filename) {
        try {
            try (InputStream in = new ResettableInputStream(data)) {
                String md5 = calculateMd5(data);
                updateFile(file, in, filename, md5, data.length());
            }
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage("Cannot upload the file: %s (%s) - %s (%s)", file, filename)
                            .handle();
        }
    }

    /**
     * Updates the given file based on the given stream.
     *
     * @param file     the S3 file to update
     * @param data     the data to store
     * @param filename the original filename to save
     * @param md5      the md5 hash value to use to verify the upload
     * @param size     the size (number of bytes) being uploaded (must be positive)
     */
    public void updateFile(@Nonnull StoredObject file,
                           @Nonnull InputStream data,
                           @Nullable String filename,
                           @Nullable String md5,
                           long size) {
        VirtualObject object = (VirtualObject) file;
        if (Strings.isFilled(md5) && Strings.areEqual(object.getMd5(), md5)) {
            return;
        }
        String newPhysicalKey = keyGen.generateId();
        String oldPhysicalKey = object.getPhysicalKey();
        try {
            PhysicalStorageEngine engine = getStorageEngine(object.getBucket());
            // Store new file
            engine.storePhysicalObject(object.getBucket(), newPhysicalKey, data, md5, size);

            object.setFileSize(size);
            object.setMd5(md5);
            if (Strings.isFilled(filename)) {
                object.setPath(filename);
            }
            object.setPhysicalKey(newPhysicalKey);
            oma.override(object);

            // Delete all (now outdated) versions
            oma.select(VirtualObjectVersion.class).eq(VirtualObjectVersion.VIRTUAL_OBJECT, object).delete();

            // Delete old file
            engine.deletePhysicalObject(object.getBucket(), oldPhysicalKey);
        } catch (IOException exception) {
            throw Exceptions.handle().to(LOG).error(exception).withNLSKey("Storage.uploadFailed").handle();
        }
    }

    /**
     * Creates a new output stream which updates the contents of the given file.
     * <p>
     * Note that most probably, the file will be updated once the stream is closed and not immediatelly on a write.
     * Also note that it is essential to close the stream to release underlying resources.
     *
     * @param file the file to update.
     * @return an output stream to write the contents to.
     */
    public OutputStream updateFile(@Nonnull StoredObject file) {
        return new UpdatingOutputStream(this, file);
    }

    /**
     * Retrieves the actual data stored by the given object.
     *
     * @param file the object to fetch the contents for
     * @return an input stream which provides the contents of the object / file
     */
    @Nonnull
    public InputStream getData(@Nonnull StoredObject file) {
        VirtualObject object = (VirtualObject) file;

        InputStream result = getStorageEngine(object.getBucket()).getData(object.getBucket(), object.getPhysicalKey());
        return Objects.requireNonNullElseGet(result, () -> new ByteArrayInputStream(EMPTY_BUFFER));
    }

    /**
     * Deletes the given object and alls of its versions.
     *
     * @param object the object to delete
     */
    public void delete(StoredObject object) {
        if (object instanceof VirtualObject virtualObject) {
            oma.delete(virtualObject);
        }
    }

    /**
     * Deletes the physical object in the given bucket.
     *
     * @param bucket      the bucket to delete the object from
     * @param physicalKey the physical key of the object to delete
     */
    protected void deletePhysicalObject(String bucket, String physicalKey) {
        getStorageEngine(bucket).deletePhysicalObject(bucket, physicalKey);
    }

    /**
     * Verifies the authentication hash for the given key.
     *
     * @param key  the key to verify
     * @param hash the hash to verify
     * @return <tt>true</tt> if the hash verifies the given object key, <tt>false</tt> otherwise
     */
    protected boolean verifyHash(String key, String hash) {
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
     * Delivers a pyhsical file or object.
     *
     * @param webContext    the request to respond to
     * @param bucket        the bucket to deliver from
     * @param physicalKey   the physical file to deliver
     * @param fileExtension the file extension of the file (to determine the <tt>Content-Type</tt>)
     */
    protected void deliverPhysicalFile(WebContext webContext, String bucket, String physicalKey, String fileExtension) {
        if (shouldLogDeprecated(bucket)) {
            DEPRECATION_LOG.WARN("A file from the deprecated storage was requested: %s",
                                 webContext.getRequest().toString());
        }

        getStorageEngine(bucket).deliver(webContext, bucket, physicalKey, fileExtension);
    }

    /**
     * Creates a builder to construct a download URL for an object.
     *
     * @param bucket    the bucket containing the object
     * @param objectKey the object to create an URL for
     * @return a builder to construct a download URL
     */
    public DownloadBuilder prepareDownload(String bucket, String objectKey) {
        if (shouldLogDeprecated(bucket)) {
            Exceptions.logDeprecatedMethodUse();
        }

        return new DownloadBuilder(this, bucket, objectKey);
    }

    /**
     * Creates a builder for download URLs based on a {@link VirtualObject} which might avoid a lookup.
     *
     * @param object the object to deliver
     * @return a builder to construct a download URL
     */
    protected DownloadBuilder prepareDownload(VirtualObject object) {
        if (shouldLogDeprecated(object.getBucket())) {
            Exceptions.logDeprecatedMethodUse();
        }

        return new DownloadBuilder(this, object);
    }

    private boolean shouldLogDeprecated(String bucket) {
        if (!DEPRECATION_LOG.isFINE()) {
            return false;
        }

        return getBucket(bucket).map(BucketInfo::shouldLogAsDeprecated).orElse(false);
    }

    /**
     * Creates a download URL for a fully populated builder.
     *
     * @param downloadBuilder the builder specifying the details of the url
     * @return a download URL for the object described by the builder
     */
    protected String createURL(DownloadBuilder downloadBuilder) {
        String result = getStorageEngine(downloadBuilder.getBucket()).createURL(downloadBuilder);
        if (result == null) {
            result = buildURL(downloadBuilder);
        }

        return result;
    }

    /**
     * Provides a facility to provide an internal download URL which utilizes {@link
     * PhysicalStorageEngine#deliver(WebContext, String, String, String)}.
     * <p>
     * This is the default way of delivering files. However, a {@link PhysicalStorageEngine} can provide its
     * own URLs which are handled outside of the system.
     *
     * @param downloadBuilder the builder specifying the details of the download
     * @return the download URL
     */
    private String buildURL(DownloadBuilder downloadBuilder) {
        StringBuilder result = new StringBuilder();
        if (Strings.isFilled(downloadBuilder.getBaseURL())) {
            result.append(downloadBuilder.getBaseURL());
        }
        result.append("/storage/physical/");
        result.append(downloadBuilder.getBucket());
        result.append("/");
        if (downloadBuilder.isEternallyValid()) {
            result.append(computeEternallyValidHash(downloadBuilder.getPhysicalKey()));
        } else {
            result.append(computeHash(downloadBuilder.getPhysicalKey(), 0));
        }
        result.append("/");
        String addonText = downloadBuilder.getAddonText();
        if (Strings.isFilled(addonText)) {
            result.append(Strings.cleanup(addonText,
                                          text -> NON_URL_CHARACTERS.matcher(text).replaceAll("-"),
                                          StringCleanup::reduceCharacters));
            result.append("--");
        }
        result.append(downloadBuilder.getPhysicalKey());
        result.append(".");
        result.append(downloadBuilder.getFileExtension());
        if (Strings.isFilled(downloadBuilder.getFilename())) {
            result.append("?name=");
            result.append(Urls.encode(downloadBuilder.getFilename()));
        }

        return result.toString();
    }

    /**
     * Computes an authentication hash for the given physical storage key and the offset in days (from the current).
     *
     * @param physicalKey the key to authenticate
     * @param offsetDays  the offset from the current day
     * @return a hash valid for the given day and key
     */
    private String computeHash(String physicalKey, int offsetDays) {
        return Hasher.md5().hash(physicalKey + getTimestampOfDay(offsetDays) + getSharedSecret()).toHexString();
    }

    /**
     * Computes an authentication hash which is eternally valid.
     *
     * @param physicalKey the key to authenticate
     * @return a hash valid forever
     */
    private String computeEternallyValidHash(String physicalKey) {
        return Hasher.md5().hash(physicalKey + getSharedSecret()).toHexString();
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
                return String.valueOf(System.currentTimeMillis());
            }
        }

        return safeSharedSecret;
    }
}
