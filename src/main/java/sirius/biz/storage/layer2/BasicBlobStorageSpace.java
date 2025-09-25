/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.locks.Locks;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.layer2.variants.BlobConversionEvent;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.layer2.variants.ConversionEngine;
import sirius.biz.storage.layer2.variants.ConversionProcess;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.KeyGenerator;
import sirius.kernel.async.Future;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Processor;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Represents a base implementation for a layer 2 storage space which manages {@link Blob blobs} and
 * {@link Directory directories}.
 *
 * @param <B> the effective subclass of {@link Blob} used by a concrete subclass
 * @param <D> the effective subclass of {@link Directory} used by a concrete subclass
 * @param <V> the effective subclass of {@link BlobVariant} used by a concrete subclass
 */
public abstract class BasicBlobStorageSpace<B extends Blob & OptimisticCreate, D extends Directory & OptimisticCreate, V extends BlobVariant>
        implements BlobStorageSpace {

    /**
     * Specifies the total number of attempts for the optimistic locking strategies used by this class.
     */
    @ConfigValue("storage.layer2.conversion.maxOptimisticLockAttempts")
    private static int maxOptimisticLockAttempts;

    /**
     * Specifies the total number of attempts to wait for a conversion result.
     */
    @ConfigValue("storage.layer2.conversion.maxConversionAttempts")
    private static int maxConversionAttempts;

    /**
     * Specifies the total number of attempts to wait for a conversion result when requested to wait longer.
     */
    @ConfigValue("storage.layer2.conversion.maxLongConversionAttempts")
    private static int maxLongConversionAttempts;

    /**
     * Specifies the number of milliseconds to wait for a conversion.
     */
    @ConfigValue("storage.layer2.conversion.conversionRetryDelay")
    private static Duration conversionRetryDelay;

    /**
     * Specifies the interval (in minutes) after which a conversion is retried for a given blob variant.
     */
    @ConfigValue("storage.layer2.conversion.hangingConversionRetryInterval")
    private static Duration hangingConversionRetryInterval;

    /**
     * Specifies the maximal number of attempts to generate a variant.
     */
    public static final int VARIANT_MAX_CONVERSION_ATTEMPTS = 3;

    /**
     * Used to cache the fact, that a blob variant cannot be created/converted.
     */
    private static final String CACHED_FAILURE_MARKER = "-";

    /**
     * Contains the name of the config key used to determine which permission is required to browse / read blobs in
     * the space.
     */
    private static final String CONFIG_KEY_PERMISSION_READ = "readPermission";

    /**
     * Contains the name of the config key used to determine which permission is required to write blobs
     * in the space.
     */
    private static final String CONFIG_KEY_PERMISSION_WRITE = "writePermission";

    /**
     * Contains the name of the config key used to determine the base url to use when generating
     * delivery URLs for this space.
     */
    private static final String CONFIG_KEY_BASE_URL = "baseUrl";

    /**
     * Contains the name of the config key used to determine if normalized blob and directory names are used.
     * <p>
     * Setting this to true makes the file system effectively case-insensitive. Whereas using false
     * makes it case-sensitive.
     * Note that this only determines case sensitivity:
     * Unicode normalization is not configured with this key, its always applied -
     * all directory and blob names are normalized to Unicode {@link Normalizer.Form#NFC combined form}.
     */
    private static final String CONFIG_KEY_USE_NORMALIZED_NAMES = "useNormalizedNames";

    /**
     * Contains the name of the config key used to determine a short description of the storage space.
     * <p>
     * Note that the result will be {@link sirius.kernel.nls.NLS#smartGet(String) smart translated}.
     */
    private static final String CONFIG_KEY_DESCRIPTION = "description";

    /**
     * Contains the name of the config key used to determine the maximal retention time in days.
     * <p>
     * If this value is non-zero, blobs older than the number of days well be deleted automatically.
     */
    private static final String CONFIG_KEY_RETENTION_DAYS = "retentionDays";

    /**
     * Contains the name of the config keys used to determine the url validity in days.
     */
    private static final String CONFIG_KEY_URL_VALIDITY_DAYS = "urlValidityDays";

    /**
     * Determines if touch tracking is active for this space.
     */
    private static final String CONFIG_KEY_TOUCH_TRACKING = "touchTracking";

    /**
     * Determines if we should sort by the last modification date instead of "by name".
     */
    private static final String CONFIG_KEY_SORT_BY_LAST_MODIFIED = "sortByLastModified";

    /**
     * Contains the name of the executor in which requests are moved which might be blocked while waiting for
     * a conversion to happen. We do not want to jam our main executor of the web server for this, therefore
     * a separator one is used.
     */
    private static final String EXECUTOR_STORAGE_CONVERSION_DELIVERY = "storage-conversion-delivery";

    /**
     * Describes if the variant was obtained from cache, lookup or delegated (where a conversion was requested).
     */
    private static final String HEADER_VARIANT_SOURCE = "X-VariantSource";

    /**
     * Contains the host which performed the conversion in case of delegated conversions.
     */
    private static final String HEADER_VARIANT_COMPUTER = "X-VariantComputer";

    /**
     * Connect timeout for delegated blob downloads
     */
    private static final int CONNECT_TIMEOUT = (int) TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS);

    /**
     * Determines the blacklisting interval if a conversion host is unreachable.
     */
    private static final Duration MAX_CONVERSION_HOST_BLACKLISTING = Duration.ofMinutes(5);

    /**
     * Determines how many times a conversion via an external host is attempted.
     * <p>
     * We might need several attempts if a delegate host is down due to maintenance
     */
    private static final int MAX_CONVERSION_DELEGATE_ATTEMPTS = 3;

    /**
     * Determines how many times we try to pick a random host for delegation.
     * <p>
     * Essentially, we pick a random delegate host and check if we have connectivity issues.
     * If we picked this many times, and every host has connectivity issues, we give up and just try this host anyway.
     */
    private static final int MAX_CONVERSION_DELEGATE_HOST_PICK_ATTEMPTS = 3;

    @Part
    protected static ObjectStorage objectStorage;

    @Part
    protected static KeyGenerator keyGenerator;

    @Part
    protected static StorageUtils utils;

    @Part
    @Nullable
    private static Locks locks;

    @Part
    protected static ConversionEngine conversionEngine;

    @Part
    private static EventRecorder eventRecorder;

    @Part
    protected static Tasks tasks;

    @PriorityParts(FailedVariantConversionHandler.class)
    private static List<FailedVariantConversionHandler> failedVariantHandlers;

    @Part
    @Nullable
    protected static TouchWritebackLoop touchWritebackLoop;

    @ConfigValue("storage.layer2.conversion.enabled")
    protected static boolean conversionEnabled;

    @ConfigValue("storage.layer2.conversion.hosts")
    protected static List<String> conversionHosts;

    /**
     * Keeps a timestamp when the last connectivity issue ({@link  java.net.ConnectException}) for a host was received.
     * <p>
     * If we detect a connectivity issue, we skip a host for up to {@link #MAX_CONVERSION_HOST_BLACKLISTING}.
     */
    protected Map<String, LocalDateTime> conversionHostLastConnectivityIssue = new ConcurrentHashMap<>();

    /**
     * Caches the {@link Directory directories} that belong to certain id
     */
    protected static Cache<String, Directory> directoryByIdCache =
            CacheManager.createCoherentCache("storage-directories");

    /**
     * Caches the {@link Blob#getFilename() file names} that belong to certain {@link Blob#getBlobKey() blob keys}
     */
    protected static Cache<String, String> blobKeyToFilenameCache =
            CacheManager.createCoherentCache("storage-filenames");

    /**
     * Caches the {@link Blob#getPhysicalObjectKey() physical keys} that belong to certain
     * {@link Blob#getBlobKey() blob keys}
     */
    protected static Cache<String, String> blobKeyToPhysicalCache =
            CacheManager.createCoherentCache("storage-physical-keys");

    protected static final String REMOVE_BY_FILENAME = "filename_remover";

    /**
     * Caches the {@link Blob blobs} that belong to certain paths
     */
    protected static Cache<String, Blob> blobByPathCache = CacheManager.<Blob>createCoherentCache("storage-paths")
                                                                       .addValueBasedRemover(REMOVE_BY_FILENAME)
                                                                       .removeIf((input, blob) -> blob == null
                                                                                                  || Strings.areEqual(
                                                                               blob.getFilename(),
                                                                               input));

    protected final Extension config;
    protected final String description;
    protected String spaceName;
    protected String readPermission;
    protected String writePermission;
    protected String baseUrl;
    protected boolean useNormalizedNames;
    protected int retentionDays;
    protected int urlValidityDays;
    protected boolean touchTracking;
    protected boolean sortByLastModified;
    protected ObjectStorageSpace objectStorageSpace;

    /**
     * Creates a new instance by loading all settings from the given config section.
     *
     * @param spaceName the name of this space
     * @param config    the configuration to read the settings from
     */
    protected BasicBlobStorageSpace(String spaceName, Extension config) {
        this.spaceName = spaceName;
        this.config = config;
        this.readPermission = config.get(CONFIG_KEY_PERMISSION_READ).asString();
        this.writePermission = config.get(CONFIG_KEY_PERMISSION_WRITE).asString();
        this.baseUrl = config.get(CONFIG_KEY_BASE_URL).getString();
        this.useNormalizedNames = config.get(CONFIG_KEY_USE_NORMALIZED_NAMES).asBoolean();
        this.description = config.getRaw(CONFIG_KEY_DESCRIPTION).asString();
        this.retentionDays = config.get(CONFIG_KEY_RETENTION_DAYS).asInt(0);
        this.urlValidityDays = config.get(CONFIG_KEY_URL_VALIDITY_DAYS).asInt(StorageUtils.DEFAULT_URL_VALIDITY_DAYS);
        this.touchTracking = config.get(CONFIG_KEY_TOUCH_TRACKING).asBoolean();
        this.sortByLastModified = config.get(CONFIG_KEY_SORT_BY_LAST_MODIFIED).asBoolean();
    }

    @Override
    public String getName() {
        return spaceName;
    }

    @Nullable
    @Override
    public String getDescription() {
        return NLS.smartGet(description);
    }

    @Nullable
    @Override
    public String getAcademyVideoTrackId() {
        return config.getString("academyVideoTrackId");
    }

    @Nullable
    @Override
    public String getAcademyVideoCode() {
        return config.getString("academyVideoCode");
    }

    @Nullable
    @Override
    public String getKnowledgeBaseArticleCode() {
        return config.getString("kba");
    }

    @Override
    public ObjectStorageSpace getPhysicalSpace() {
        if (objectStorageSpace == null) {
            objectStorageSpace = objectStorage.getSpace(spaceName);
        }
        return objectStorageSpace;
    }

    @Override
    public boolean isBrowsable() {
        return UserContext.getCurrentUser().hasPermission(readPermission);
    }

    @Override
    public boolean isReadonly() {
        return !UserContext.getCurrentUser().hasPermission(writePermission);
    }

    @Override
    public boolean isTouchTracking() {
        return touchTracking;
    }

    @Override
    public int getRetentionDays() {
        return retentionDays;
    }

    @Override
    public int getUrlValidityDays() {
        return urlValidityDays;
    }

    /**
     * Provides the skeleton of most of the optimistic locking algorithms used by this class.
     * <p>
     * Some parts of this storage space need to fulfill some invariants (e.g. a filename is unique within a directory).
     * However, most of the time, these won't be falsified, even in concurrent use. Therefore, we use an optimistic
     * locking approach. This is essentially a fast-path (create a data object) without any locking or other protection
     * and then check if an invariant holds. If so (which is expected) commit the change, otherwise, rollback and retry.
     *
     * @param lookup          provides the callback which performs the lookup to determine if the desired data
     *                        object already exists
     * @param factory         creates a new data object if none was found
     * @param correctnessTest determines if the invariant holds after the data object was created
     * @param commit          marks the data object as usable for others (it will now pass the <tt>viabilityTest</tt>)
     * @param rollback        undoes the change performed by the <tt>factory</tt> as the invariant no longer holds
     * @param errorHandler    invoked in case of an exception or when running out of retries
     * @param <R>             the type of the data object to find or create
     * @return the entity which was either found by <tt>lookup</tt> or created via the <tt>factory</tt>
     */
    protected <R extends OptimisticCreate> R findOrCreateWithOptimisticLock(Producer<R> lookup,
                                                                            Producer<R> factory,
                                                                            Processor<R, Boolean> correctnessTest,
                                                                            Callback<R> commit,
                                                                            Callback<R> rollback,
                                                                            Function<Exception, HandledException> errorHandler) {
        int attempts = maxOptimisticLockAttempts;
        try {
            while (attempts-- > 0) {
                R result = tryFindOrCreate(lookup, factory, correctnessTest, commit, rollback);
                if (result != null) {
                    return result;
                }

                Wait.randomMillis(0, 250);
            }

            throw errorHandler.apply(new IllegalStateException(Strings.apply(
                    "Failed to execute an optimistic locked update after %s retries",
                    maxOptimisticLockAttempts)));
        } catch (Exception exception) {
            throw errorHandler.apply(exception);
        }
    }

    private <R extends OptimisticCreate> R tryFindOrCreate(Producer<R> lookup,
                                                           Producer<R> factory,
                                                           Processor<R, Boolean> correctnessTest,
                                                           Callback<R> commit,
                                                           Callback<R> rollback) throws Exception {
        R result = lookup.create();
        if (result != null) {
            if (result.isCommitted()) {
                return result;
            }
        } else {
            result = factory.create();
            if (Boolean.TRUE.equals(correctnessTest.apply(result))) {
                commit.invoke(result);
                return result;
            } else {
                rollback.invoke(result);
            }
        }

        return null;
    }

    @Override
    public Directory getRoot(String tenantId) {
        return findOrCreateWithOptimisticLock(() -> findRoot(tenantId),
                                              () -> createRoot(tenantId),
                                              this::isSingularRoot,
                                              this::commitDirectory,
                                              this::rollbackDirectory,
                                              error -> Exceptions.handle()
                                                                 .to(StorageUtils.LOG)
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the root directory for space '%s' and tenant '%s'.",
                                                                         spaceName,
                                                                         tenantId)
                                                                 .handle());
    }

    @Override
    public Optional<? extends Blob> findByPath(String tenantId, String path) {
        String sanitizedPath = utils.sanitizePath(path);

        if (Strings.isEmpty(sanitizedPath)) {
            return Optional.empty();
        }

        return Optional.ofNullable(blobByPathCache.get(determinePathCacheKey(tenantId, sanitizedPath),
                                                       ignored -> fetchByPath(tenantId, sanitizedPath)));
    }

    @Nonnull
    private String determinePathCacheKey(String tenantId, String path) {
        return spaceName + "-" + tenantId + "-" + (useNormalizedNames ? path.toLowerCase() : path);
    }

    protected Blob fetchByPath(String tenantId, @Nonnull String path) {
        String[] parts = path.split("/");
        Directory currentDirectory = getRoot(tenantId);
        int index = 0;
        while (currentDirectory != null && index < parts.length - 1) {
            currentDirectory = currentDirectory.findChildDirectory(parts[index]).orElse(null);
            index++;
        }

        if (currentDirectory != null) {
            return currentDirectory.findChildBlob(parts[index]).orElse(null);
        } else {
            return null;
        }
    }

    @Override
    public Optional<? extends Blob> findByPath(String path) {
        return findByPath(UserContext.getCurrentUser().getTenantId(), path);
    }

    @Override
    public Blob findOrCreateByPath(String tenantId, String path) {
        String sanitizedPath = utils.sanitizePath(path);

        if (Strings.isEmpty(sanitizedPath)) {
            throw new IllegalArgumentException("An empty path was provided!");
        }

        String key = determinePathCacheKey(tenantId, sanitizedPath);
        Blob blob = blobByPathCache.get(key);
        if (blob == null) {
            // Ensure the cache entry is invalidated on all nodes
            blobByPathCache.remove(key);

            blob = fetchOrCreateByPath(tenantId, sanitizedPath);
            blobByPathCache.put(key, blob);
        }
        return blob;
    }

    protected Blob fetchOrCreateByPath(String tenantId, @Nonnull String path) {
        String[] parts = path.split("/");
        Directory currentDirectory = getRoot(tenantId);
        int index = 0;
        while (index < parts.length - 1) {
            currentDirectory = currentDirectory.findOrCreateChildDirectory(parts[index]);
            index++;
        }

        return currentDirectory.findOrCreateChildBlob(parts[index]);
    }

    @Override
    public Blob findOrCreateByPath(String path) {
        return findOrCreateByPath(UserContext.getCurrentUser().getTenantId(), path);
    }

    /**
     * Tries to find the root directory for the given tenant.
     * <p>
     * This is the <b>lookup</b> of {@link #getRoot(String)}, therefore it will find uncommitted directories.
     *
     * @param tenantId the id of the tenant to find the root directory for
     * @return the root directory or <tt>null</tt> if it doesn't exist yet
     */
    @Nullable
    protected abstract D findRoot(String tenantId);

    /**
     * Creates the root directory for the given tenant.
     * <p>
     * This is the <b>factory</b> of {@link #getRoot(String)}.
     *
     * @param tenantId the id of the tenant to create the root directory for
     * @return the root directory for the given tenant
     */
    protected abstract D createRoot(String tenantId);

    /**
     * Determines if the given directory is the only root directory for this tenant and space.
     * <p>
     * This is the <b>correctnessTest</b> of {@link #getRoot(String)}.
     *
     * @param directory the directory to check
     * @return <tt>true</tt> if it is unique, <tt>false</tt> otherwise
     */
    protected abstract boolean isSingularRoot(D directory);

    /**
     * Commits a directory.
     * <p>
     * This is the <tt>commit</tt> callback for all optimistic locking algorithms concerning directories.
     *
     * @param directory the directory to commit
     */
    protected abstract void commitDirectory(D directory);

    /**
     * Performs a rollback for the given directory.
     * <p>
     * This is the <tt>rollback</tt> callback for all optimistic locking algorithms concerning directories.
     *
     * @param directory the directory to delete
     */
    protected abstract void rollbackDirectory(D directory);

    @Override
    public Blob findOrCreateAttachedBlobByName(String referencingEntity, String filename) {
        if (Strings.isEmpty(referencingEntity)) {
            throw new IllegalArgumentException("referencingEntity must not be empty");
        }

        return findOrCreateWithOptimisticLock(() -> findAnyAttachedBlobByName(referencingEntity, filename),
                                              () -> createAttachedBlobByName(referencingEntity, filename),
                                              blob -> isAttachedBlobUnique(referencingEntity, filename, blob),
                                              this::commitBlob,
                                              this::rollbackBlob,
                                              error -> Exceptions.handle()
                                                                 .to(StorageUtils.LOG)
                                                                 .error(error)
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the attached blob for space '%s' and reference '%s' with filename '%s': %s (%s)",
                                                                         spaceName,
                                                                         referencingEntity,
                                                                         filename)
                                                                 .handle());
    }

    /**
     * Tries to find the blob with the given name which is attached to the given entity.
     * <p>
     * This is the <b>lookup</b> of {@link #findOrCreateAttachedBlobByName(String, String)}, therefore it
     * will also find  uncommitted blobs.
     *
     * @param referencingEntity the entity which references the blob via a {@link BlobContainer}.
     * @param filename          the filename to lookup
     * @return the blob with the given filename which has been attached to the given entity or <tt>null</tt>
     * if that blob doesn't exist
     */
    @Nullable
    protected abstract B findAnyAttachedBlobByName(String referencingEntity, String filename);

    /**
     * Creates a new blob with the given filename which is attached to the referencing entity.
     * <p>
     * This is the <b>factory</b> of {@link #findOrCreateAttachedBlobByName(String, String)}.
     *
     * @param referencingEntity the entity which references the blob via a {@link BlobContainer}.
     * @param filename          the filename to use
     * @return the newly created blob
     */
    protected abstract B createAttachedBlobByName(String referencingEntity, String filename);

    /**
     * Determines if the attached blob is unique within the entity reference.
     * <p>
     * This is the <b>correctnessTest</b> of {@link #findOrCreateAttachedBlobByName(String, String)}.
     *
     * @param referencingEntity the entity which references the blob via a {@link BlobContainer}.
     * @param filename          the filename to check
     * @param blob              the blob to check
     * @return <tt>true</tt> if there is only one blob with this filename referenced to the given entity.
     */
    protected abstract boolean isAttachedBlobUnique(String referencingEntity, String filename, B blob);

    /**
     * Commits a blob.
     * <p>
     * This is the <tt>commit</tt> callback for all optimistic locking algorithms concerning blobs.
     *
     * @param blob the blob to commit
     */
    protected abstract void commitBlob(B blob);

    /**
     * Performs a rollback for the given blob.
     * <p>
     * This is the <tt>rollback</tt> callback for all optimistic locking algorithms concerning blobs.
     *
     * @param blob the blob to delete
     */
    protected abstract void rollbackBlob(B blob);

    /**
     * Tries to find or create the child directory with the given name.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the directory
     * @return the directory with the given name within the given parent
     */
    public Directory findOrCreateChildDirectory(D parent, String childName) {
        return findOrCreateWithOptimisticLock(() -> findAnyChildDirectory(parent, childName),
                                              () -> createChildDirectory(parent, childName),
                                              childDirectory -> isChildDirectoryUnique(parent,
                                                                                       childName,
                                                                                       childDirectory),
                                              this::commitDirectory,
                                              this::rollbackDirectory,
                                              error -> Exceptions.handle()
                                                                 .to(StorageUtils.LOG)
                                                                 .error(error)
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the child directory for space '%s' and parent directory '%s' with name '%s': %s (%s)",
                                                                         spaceName,
                                                                         parent,
                                                                         childName)
                                                                 .handle());
    }

    /**
     * Tries to find the child directory with the given name in the given directory.
     * <p>
     * This is the <b>lookup</b> of {@link #findOrCreateChildDirectory(D, String)}, therefore it
     * will also find uncommitted directories.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the directory
     * @return the child directory with the given name or <tt>null</tt> if no child was found
     */
    @Nullable
    protected abstract D findAnyChildDirectory(D parent, String childName);

    /**
     * Creates a new directory with the given name and parent directory.
     * <p>
     * This is the <b>factory</b> of {@link #findOrCreateChildDirectory(D, String)}.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the directory
     * @return the newly created directory
     */
    protected abstract D createChildDirectory(D parent, String childName);

    /**
     * Determines if the child directory is unique within the parent directory.
     * <p>
     * This is the <b>correctnessTest</b> of {@link #findOrCreateChildDirectory(D, String)}.
     *
     * @param parent         the parent directory to search in
     * @param childName      the name of the directory
     * @param childDirectory the directory to check
     * @return <tt>true</tt> if there is only one child directory with this name in the given parent directory
     */
    protected abstract boolean isChildDirectoryUnique(D parent, String childName, D childDirectory);

    /**
     * Tries to find or create the child blob with the given name.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the blob
     * @return the blob with the given name within the given parent
     */
    public Blob findOrCreateChildBlob(D parent, String childName) {
        return findOrCreateWithOptimisticLock(() -> findAnyChildBlob(parent, childName),
                                              () -> createChildBlob(parent, childName),
                                              childBlob -> isChildBlobUnique(parent, childName, childBlob),
                                              this::commitBlob,
                                              this::rollbackBlob,
                                              error -> Exceptions.handle()
                                                                 .to(StorageUtils.LOG)
                                                                 .error(error)
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the child blob for space '%s' and parent directory '%s' with name '%s': %s (%s)",
                                                                         spaceName,
                                                                         parent,
                                                                         childName)
                                                                 .handle());
    }

    /**
     * Tries to find the child blob with the given name in the given directory.
     * <p>
     * This is the <b>lookup</b> of {@link #findOrCreateChildBlob(D, String)}, therefore it
     * will also find uncommitted blobs.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the blob
     * @return the child blob with the given name or <tt>null</tt> if no child was found
     */
    @Nullable
    protected abstract B findAnyChildBlob(D parent, String childName);

    /**
     * Creates a new blob with the given name and parent directory.
     * <p>
     * This is the <b>factory</b> of {@link #findOrCreateChildBlob(D, String)}.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the blob
     * @return the newly created blob
     */
    protected abstract B createChildBlob(D parent, String childName);

    /**
     * Determines if the child blob is unique within the parent directory.
     * <p>
     * This is the <b>correctnessTest</b> of {@link #findOrCreateChildBlob(D, String)}.
     *
     * @param parent    the parent directory to search in
     * @param childName the name of the blob
     * @param childBlob the blob to check
     * @return <tt>true</tt> if there is only one child blob with this name in the given parent directory
     */
    protected abstract boolean isChildBlobUnique(D parent, String childName, B childBlob);

    @Nullable
    protected Directory fetchDirectoryById(String idAsString) {
        if (Strings.isEmpty(idAsString)) {
            return null;
        }

        return directoryByIdCache.get(idAsString, this::lookupDirectoryById);
    }

    /**
     * Performs a lookup to fetch the directory with the given id.
     *
     * @param idAsString the id of the directory represented as string
     * @return the directory with the given id or <tt>null</tt> if no such directory exists
     */
    @Nullable
    protected abstract D lookupDirectoryById(String idAsString);

    /**
     * Determines the effective path of a given directory.
     *
     * @param directory the directory to determine the path for
     * @return the path to the given directory
     */
    @Nonnull
    public String determineDirectoryPath(@Nonnull D directory) {
        Directory parent = directory.getParent();
        if (parent == null) {
            return "/" + directory.getName();
        }

        return parent.getPath() + "/" + directory.getName();
    }

    /**
     * Determines the effective path of a given blob.
     *
     * @param blob the blob to determine the path for
     * @return the path to the given blob
     */
    @Nonnull
    public String determineBlobPath(@Nonnull B blob) {
        Directory parent = blob.getParent();
        if (parent == null) {
            if (Strings.isFilled(blob.getFilename())) {
                return blob.getFilename();
            } else {
                return blob.getBlobKey();
            }
        }

        return parent.getPath() + "/" + blob.getFilename();
    }

    /**
     * Moves the given directory into the given parent.
     *
     * @param directory the directory to move
     * @param newParent the new parent to move the directory into
     */
    public void moveDirectory(@Nonnull D directory, D newParent) {
        if (newParent == null || !Strings.areEqual(directory.getTenantId(), newParent.getTenantId())) {
            handleTenantMismatch(directory, newParent);
            return;
        }

        if (!Strings.areEqual(directory.getSpaceName(), newParent.getSpaceName())) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotMoveAcrossSpaces").handle();
        }

        if (newParent.hasChildNamed(directory.getName())) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotMoveDuplicateName").handle();
        }

        detectAndPreventCircularReference(directory, newParent);

        updateDirectoryParent(directory, newParent);

        directoryByIdCache.remove(directory.getIdAsString());
        blobByPathCache.clear();
    }

    /**
     * Detects a circular reference before moving a directory to a new home.
     *
     * @param directory the directory to move
     * @param newParent the new parent to move the directory into
     * @throws HandledException if a circular reference was detected
     */
    private void detectAndPreventCircularReference(@Nonnull D directory, D newParent) {
        Directory check = newParent;
        while (check != null && !Objects.equals(directory, check)) {
            check = check.getParent();
        }

        if (check != null) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotMoveIntoLoop").handle();
        }
    }

    private void handleTenantMismatch(Object objectToModify, D newParent) {
        throw Exceptions.handle()
                        .to(StorageUtils.LOG)
                        .withSystemErrorMessage("Layer2: Invalid parent (%s) for %s was given!",
                                                newParent,
                                                objectToModify)
                        .handle();
    }

    /**
     * Deletes the given directory.
     *
     * @param directory the directory to delete
     */
    public void deleteDirectory(D directory) {
        markDirectoryAsDeleted(directory);

        blobByPathCache.clear();
    }

    /**
     * Marks the given directory as deleted.
     *
     * @param directory the directory to delete
     */
    protected abstract void markDirectoryAsDeleted(D directory);

    /**
     * Effectively updates the parent of the given directory.
     * <p>
     * This is invoked after all checks have been passed.
     *
     * @param directory the directory to move
     * @param newParent the new parent to move the directory into
     */
    protected abstract void updateDirectoryParent(D directory, D newParent);

    /**
     * Renames the directory to the new name.
     *
     * @param directory the directory to rename
     * @param newName   the new name to use
     */
    public void renameDirectory(D directory, String newName) {
        Directory parent = directory.getParent();
        if (parent == null) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotRenameRoot").handle();
        }
        if (parent.hasChildNamed(newName)) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotRenameDuplicateName").handle();
        }

        updateDirectoryName(directory, newName);

        directoryByIdCache.remove(directory.getIdAsString());
        blobByPathCache.clear();
    }

    /**
     * Effectively updates the directory name after all checks have been passed.
     *
     * @param directory the directory to rename
     * @param newName   the new name to use
     */
    protected abstract void updateDirectoryName(D directory, String newName);

    /**
     * Moves the given blob into the given parent.
     *
     * @param blob      the blob to move
     * @param newParent the new parent to move the directory into
     */
    public void move(B blob, @Nullable D newParent) {
        if (newParent == null || !Strings.areEqual(blob.getTenantId(), newParent.getTenantId())) {
            handleTenantMismatch(blob, newParent);
        }

        if (!Strings.areEqual(blob.getSpaceName(), newParent.getSpaceName())) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotMoveAcrossSpaces").handle();
        }

        if (newParent.hasChildNamed(blob.getFilename())) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotMoveDuplicateName").handle();
        }

        updateBlobParent(blob, newParent);

        blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
    }

    /**
     * Effectively updates the parent of the given blob.
     * <p>
     * This is invoked after all checks have been passed.
     *
     * @param blob      the blob to move
     * @param newParent the new parent to move the directory into
     */
    protected abstract void updateBlobParent(B blob, D newParent);

    /**
     * Deletes the given blob.
     *
     * @param blob the blob to delete
     */
    public void delete(B blob) {
        markBlobAsDeleted(blob);

        blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
    }

    /**
     * Marks the given blob as deleted.
     *
     * @param blob the blob to delete
     */
    protected abstract void markBlobAsDeleted(B blob);

    /**
     * Renames the blob to the new name.
     *
     * @param blob    the blob to rename
     * @param newName the new name to use
     */
    public void rename(B blob, String newName) {
        if (blob.getParent().hasChildNamed(newName, blob)) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotRenameDuplicateName").handle();
        }

        blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
        updateBlobName(blob, newName);

        blobKeyToFilenameCache.remove(blob.getBlobKey());
        blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
    }

    /**
     * Effectively updates the blob name after all checks have been passed.
     *
     * @param blob    the blob to rename
     * @param newName the new name to use
     */
    protected abstract void updateBlobName(B blob, String newName);

    /**
     * Effectively updates the blob read-only flag after all checks have been passed.
     *
     * @param blob     the blob to rename
     * @param readOnly the new value to use
     */
    public abstract void updateBlobReadOnlyFlag(B blob, boolean readOnly);

    /**
     * Performs a download / fetch of the given blob to make its data locally accessible.
     * <p>
     * Note that the returned {@link FileHandle} must be closed once the data has been processed to ensure proper cleanup.
     * Do this ideally with a {@code try-with-resources} block:
     * <pre>
     * space.download(blob).ifPresent(handle -> {
     *     try (handle) {
     *         // Read from the handle here...
     *     }
     * });
     * </pre>
     *
     * @param blob the blob to fetch the data for
     * @return a {@linkplain java.io.Closeable closeable} file handle which makes the blob data accessible, or an empty optional if no data was present
     */
    public Optional<FileHandle> download(Blob blob) {
        if (Strings.isEmpty(blob.getPhysicalObjectKey())) {
            return Optional.empty();
        }

        touch(blob.getBlobKey());
        return getPhysicalSpace().download(blob.getPhysicalObjectKey());
    }

    /**
     * Downloads and provides the contents of the requested blob with the given variant.
     * <p>
     * Note: This method will not be able to return a file if the blob doesn't yet exist in the requested variant and
     * the conversion is disabled on this node (see {@link #conversionEnabled}).
     *
     * @param blobKey    the blob key of the blob to download
     * @param variant    the variant of the blob to download. Use {@link URLBuilder#VARIANT_RAW} to download the blob itself
     * @param waitLonger whether to wait longer for the conversion to complete
     * @return a handle to the given object wrapped as optional or an empty one if the object doesn't exist or couldn't
     * be converted
     */
    @Override
    public Optional<FileHandle> download(String blobKey, String variant, boolean waitLonger) {
        touch(blobKey);

        try {
            // first, attempt to resolve the physical key for the given blob and variant in a non-blocking manner; if
            // the variant has been created before, independent of whether this node has conversion enabled, this will
            // lead to a physical key that can be used to download the blob
            Tuple<String, Boolean> physicalKey = tryFetchPhysicalKey(blobKey, variant);

            // if a physical key cannot be determined, the blob has not yet been created in the requested variant; we
            // now need to check if this node is allowed to perform the conversion itself; if so, we run the physical
            // key lookup again in a blocking manner to ensure that the conversion is performed
            if (conversionEnabled && physicalKey == null) {
                physicalKey = tryCreatePhysicalKey(blobKey, variant, waitLonger);
            }

            // if the physical key is still null, this node is not allowed to perform the conversion itself or
            // conversion on this node failed gracefully; in this case, we attempt to delegate the conversion
            if (physicalKey == null) {
                return tryDelegateDownload(blobKey, variant, MAX_CONVERSION_DELEGATE_ATTEMPTS, waitLonger);
            }

            return getPhysicalSpace().download(physicalKey.getFirst());
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    /**
     * Used if the requested variant doesn't exist but the local node is not permitted to perform the conversion itself.
     * <p>
     * In this case, we attempt to download the variant from one of our known conversion servers.
     *
     * @param blobKey           the blob key of the blob to download
     * @param variant           the variant of the blob to download
     * @param remainingAttempts the max number of attempts to re-try in case of a connectivity problem
     * @param waitLonger        whether to wait longer for the conversion to complete
     * @return a handle to the given object wrapped as optional or an empty one if the object doesn't exist or couldn't
     * be converted
     * @throws IOException in case of an IO error
     */
    private Optional<FileHandle> tryDelegateDownload(String blobKey,
                                                     String variant,
                                                     int remainingAttempts,
                                                     boolean waitLonger) throws IOException {
        Optional<URL> url = determineDelegateConversionUrl(blobKey, variant, waitLonger);

        if (url.isEmpty()) {
            return Optional.empty();
        }

        int maxConversionTime =
                (int) conversionRetryDelay.multipliedBy(waitLonger ? maxLongConversionAttempts : maxConversionAttempts)
                                          .toMillis();

        URLConnection connection = url.get().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        // Uses the expected maximum conversion time with the fixed connection timeout as a buffer.
        connection.setReadTimeout(CONNECT_TIMEOUT + maxConversionTime);
        connection.connect();

        File temporaryFile = File.createTempFile("delegate-", null);

        try (FileOutputStream out = new FileOutputStream(temporaryFile); InputStream in = connection.getInputStream()) {
            Streams.transfer(in, out);
        } catch (ConnectException connectException) {
            Files.delete(temporaryFile);
            // The current conversion host cannot be accessed...
            if (conversionHosts.size() > 1 && remainingAttempts > 1) {
                // but we have several to pick from, so lets try again
                Exceptions.ignore(connectException);
                recordHostConnectivityIssue(url.get().getHost());
                return tryDelegateDownload(blobKey, variant, remainingAttempts - 1, waitLonger);
            } else {
                throw connectException;
            }
        } catch (IOException exception) {
            Files.delete(temporaryFile);
            throw exception;
        }

        return Optional.of(FileHandle.temporaryFileHandle(temporaryFile));
    }

    /**
     * Provides read access via an {@link InputStream}.
     *
     * @param blob the blob to fetch the data for
     * @return an input stream to read and process the contents of the blob
     */
    public InputStream createInputStream(Blob blob) {
        touch(blob.getBlobKey());

        return getPhysicalSpace().getInputStream(blob.getPhysicalObjectKey())
                                 .orElseThrow(() -> Exceptions.handle()
                                                              .to(StorageUtils.LOG)
                                                              .withSystemErrorMessage(
                                                                      "Layer 2: Cannot obtain an InputStream for %s (%s)",
                                                                      blob.getBlobKey(),
                                                                      blob.getFilename())
                                                              .handle());
    }

    /**
     * Updates the contents of the given blob with the given file and optional filename.
     *
     * @param blob     the blob to update
     * @param filename the filename to update (if set)
     * @param file     the file to read the data from
     */
    public void updateContent(B blob, @Nullable String filename, File file) {
        String nextPhysicalId = keyGenerator.generateId();
        try {
            if (Strings.isFilled(filename)) {
                blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
            }
            getPhysicalSpace().upload(nextPhysicalId, file);
            blobKeyToPhysicalCache.remove(buildCacheLookupKey(blob.getBlobKey(), URLBuilder.VARIANT_RAW));
            Optional<String> previousPhysicalId = updateBlob(blob, nextPhysicalId, file.length(), filename);
            if (previousPhysicalId.isPresent()) {
                deleteBlobVariants(blob);
                getPhysicalSpace().delete(previousPhysicalId.get());
            }

            blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
        } catch (Exception exception) {
            try {
                getPhysicalSpace().delete(nextPhysicalId);
            } catch (Exception innnerException) {
                Exceptions.ignore(innnerException);
            }

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 2: Cannot update the contents of %s in %s: %s (%s)",
                                                    blob.getBlobKey(),
                                                    spaceName)
                            .handle();
        }
    }

    /**
     * Updates the blob metadata after the contents have been saved as a layer 1 physical object.
     *
     * @param blob           the blob to update
     * @param nextPhysicalId the physical id to store which contains the new contents
     * @param size           the size of the data
     * @param filename       the new filename to use (if set)
     * @return the previous physical id which can now be deleted or an empty optional if there was no previous content
     * @throws Exception in case of an error while updating the metadata
     */
    @Nonnull
    protected abstract Optional<String> updateBlob(@Nonnull B blob,
                                                   @Nonnull String nextPhysicalId,
                                                   long size,
                                                   @Nullable String filename) throws Exception;

    /**
     * Updates the contents of the given blob with the given stream data and optional filename.
     *
     * @param blob          the blob to update
     * @param filename      the filename to update (if set)
     * @param data          the data to store into the given blob
     * @param contentLength the expected number of bytes in the given stream
     */
    public void updateContent(B blob, String filename, InputStream data, long contentLength) {
        String nextPhysicalId = keyGenerator.generateId();
        try {
            if (Strings.isFilled(filename)) {
                blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
            }
            getPhysicalSpace().upload(nextPhysicalId, data, contentLength);
            blobKeyToPhysicalCache.remove(buildCacheLookupKey(blob.getBlobKey(), URLBuilder.VARIANT_RAW));
            Optional<String> previousPhysicalId = updateBlob(blob, nextPhysicalId, contentLength, filename);
            if (previousPhysicalId.isPresent()) {
                deleteBlobVariants(blob);
                getPhysicalSpace().delete(previousPhysicalId.get());
            }

            blobByPathCache.removeAll(REMOVE_BY_FILENAME, blob.getFilename());
        } catch (Exception exception) {
            try {
                getPhysicalSpace().delete(nextPhysicalId);
            } catch (Exception innerException) {
                Exceptions.ignore(innerException);
            }
            if (exception instanceof InterruptedIOException || exception.getCause() instanceof InterruptedIOException) {
                throw Exceptions.createHandled()
                                .error(exception)
                                .withSystemErrorMessage("Layer 2: Interrupted updating contents of %s in %s: %s (%s)",
                                                        blob.getBlobKey(),
                                                        spaceName)
                                .handle();
            }

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 2: Cannot update the contents of %s in %s: %s (%s)",
                                                    blob.getBlobKey(),
                                                    spaceName)
                            .handle();
        }
    }

    private void deleteBlobVariants(B blob) {
        blob.fetchVariants().forEach(BlobVariant::delete);
    }

    /**
     * Removes the given variant from physical key cache
     *
     * @param blob        the parent blob of the variant
     * @param variantName the variant name of the variant
     */
    protected abstract void purgeVariantFromCache(B blob, String variantName);

    /**
     * Creates a local buffer and provides an {@link OutputStream} which can be used to update the contents of the given blob.
     *
     * @param blob              the blob to update
     * @param filename          the new filename to use
     * @param completedCallback the callback to invoke once the output stream is closed and the blob has been updated
     * @return an output stream which will be stored in the given blob once the stream is closed
     */
    public OutputStream createOutputStream(B blob, @Nullable String filename, @Nullable Runnable completedCallback) {
        try {
            return utils.createLocalBuffer(file -> {
                try {
                    updateContent(blob, filename, file);
                    if (completedCallback != null) {
                        completedCallback.run();
                    }
                } finally {
                    Files.delete(file);
                }
            });
        } catch (IOException exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 2: Cannot create a local buffer to provide an output stream for %s (%s) in %s: %s (%s)",
                                    blob.getBlobKey(),
                                    blob.getFilename(),
                                    spaceName)
                            .handle();
        }
    }

    @Override
    public Optional<String> resolveFilename(String blobKey) {
        return Optional.ofNullable(blobKeyToFilenameCache.get(spaceName + "-" + blobKey,
                                                              ignored -> findByBlobKey(blobKey).map(Blob::getFilename)
                                                                                               .orElse(null)));
    }

    @Override
    public void deliver(String blobKey, String variant, Response response, Runnable markAsLongRunning) {
        touch(blobKey);

        try {
            Tuple<String, Boolean> physicalKey = tryFetchPhysicalKey(blobKey, variant);
            if (physicalKey != null) {
                response.addHeader(HEADER_VARIANT_SOURCE,
                                   Boolean.TRUE.equals(physicalKey.getSecond()) ? "cache" : "lookup");
                getPhysicalSpace().deliver(response, physicalKey.getFirst(), false);
            } else {
                if (markAsLongRunning != null) {
                    markAsLongRunning.run();
                }

                deliverAsync(blobKey, variant, response);
            }
        } catch (IllegalArgumentException exception) {
            response.notCached().error(HttpResponseStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception _) {
            response.notCached().error(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deliverPhysical(@Nullable String blobKey,
                                @Nonnull String physicalKey,
                                @Nonnull Response response,
                                boolean largeFileExpected) {
        touch(blobKey);
        getPhysicalSpace().deliver(response, physicalKey, largeFileExpected);
    }

    /**
     * Marks the given blob as touched.
     * <p>
     * We internally de-duplicate touch events via {@link TouchWritebackLoop}.
     *
     * @param blobKey the blob to mark as touched
     */
    public void touch(String blobKey) {
        if (isTouchTracking() && Strings.isFilled(blobKey)) {
            touchWritebackLoop.markTouched(spaceName, blobKey);
        }
    }

    /**
     * Resolves the physical key for the given blob and variant.
     * <p>
     * The physical key is the identifier of the layer 1 object which will be delivered to the user. Depending on
     * the requested variant, this is either {@link Blob#getPhysicalObjectKey()} (when {@link URLBuilder#VARIANT_RAW}
     * is used) or the {@link BlobVariant#getPhysicalObjectKey()} of the requested variant.
     *
     * @param blobKey     the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @return the physical key or <tt>null</tt> if the appropriate variant wasn't found. We also return an indicator
     * if the key was read from the internal cache or if a lookup was required.
     * @throws HandledException         if a requested variant cannot be computed
     * @throws IllegalArgumentException if an unknown variant is requested
     */
    protected Tuple<String, Boolean> tryFetchPhysicalKey(String blobKey, String variantName) {
        String variantCacheKey = buildCacheLookupKey(blobKey, variantName);
        String cachedPhysicalVariantKey = blobKeyToPhysicalCache.get(variantCacheKey);
        if (Strings.isFilled(cachedPhysicalVariantKey)) {
            if (CACHED_FAILURE_MARKER.equals(cachedPhysicalVariantKey)) {
                throwExhaustedConversionAttemptsException(blobKey, variantName);
            }
            return Tuple.create(cachedPhysicalVariantKey, true);
        }

        try {
            String physicalKey = lookupPhysicalKey(blobKey, variantName);
            if (physicalKey != null) {
                blobKeyToPhysicalCache.put(variantCacheKey, physicalKey);
                return Tuple.create(physicalKey, false);
            } else {
                return null;
            }
        } catch (Exception exception) {
            // The conversion ultimately failed, we can therefore cache the result, as no more conversion attempts
            // will happen...
            blobKeyToPhysicalCache.put(variantCacheKey, CACHED_FAILURE_MARKER);
            throw exception;
        }
    }

    /**
     * Creates the physical key for the given blob and variant.
     *
     * @param blobKey     the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param waitLonger  whether to wait longer for the conversion to complete
     * @return the physical key or <tt>null</tt> if the appropriate variant wasn't found. We also return an indicator
     * if the key was read from the internal cache or if a lookup was required.
     * @throws HandledException         if a requested variant cannot be computed
     * @throws IllegalArgumentException if an unknown variant is requested
     */
    @SuppressWarnings("unchecked")
    protected Tuple<String, Boolean> tryCreatePhysicalKey(String blobKey, String variantName, boolean waitLonger) {
        Tuple<String, Boolean> keyAndCache = tryFetchPhysicalKey(blobKey, variantName);
        if (keyAndCache != null) {
            return keyAndCache;
        }

        B blob = (B) findByBlobKey(blobKey).orElse(null);
        if (blob == null) {
            return null;
        }

        V variant = findOrCreateVariant(blob, variantName, waitLonger);

        if (variant == null || Strings.isEmpty(variant.getPhysicalObjectKey())) {
            return null;
        } else {
            return Tuple.create(variant.getPhysicalObjectKey(), false);
        }
    }

    protected String buildCacheLookupKey(String blobKey, String variantName) {
        return spaceName + "-" + blobKey + "-" + variantName;
    }

    /**
     * Performs the actual lookup of the physical key for the given blob and variant.
     *
     * @param blobKey     the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @return the physical key or <tt>null</tt> if the appropriate variant wasn't found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    protected String lookupPhysicalKey(String blobKey, @Nonnull String variantName) {
        B blob = (B) findByBlobKey(blobKey).orElse(null);
        if (blob == null) {
            return null;
        }

        if (URLBuilder.VARIANT_RAW.equals(variantName)) {
            return blob.getPhysicalObjectKey();
        }

        if (!conversionEngine.isKnownVariant(variantName)) {
            throw new IllegalArgumentException(Strings.apply("Unknown variant type: %s", variantName));
        }

        V variant = tryFetchVariant(blob, variantName);

        if (variant == null || Strings.isEmpty(variant.getPhysicalObjectKey())) {
            return null;
        } else {
            return variant.getPhysicalObjectKey();
        }
    }

    /**
     * Tries to find the requested variant for the given blob and creates it if missing.
     * <p>
     * Note that there this is a recursive optimistic locking algorithm at work where
     * {@link #attemptToFindOrCreateVariant(Blob, String, int)} and
     * {@link #awaitConversionResultAndRetryToFindVariant(Blob, String, int)} build the "loop".
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param waitLonger  whether to wait longer for the conversion to complete
     * @return the variant for the given blob with the given name or null if no such variant exists
     */
    @Nullable
    protected V findOrCreateVariant(B blob, String variantName, boolean waitLonger) {
        try {
            int retries = waitLonger ? maxLongConversionAttempts : maxConversionAttempts;
            return attemptToFindOrCreateVariant(blob, variantName, retries);
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 2: Failed to find or create the variant %s of %s in %s: %s (%s)",
                                    variantName,
                                    blob.getBlobKey(),
                                    spaceName)
                            .handle();
        }
    }

    /**
     * Tries to find the requested variant in the database and checks if the variant has already been converted successfully.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @return the variant for the given blob with the given name or null if no such variant exists
     */
    private V tryFetchVariant(B blob, String variantName) {
        V variant = findAnyVariant(blob, variantName);
        if (variant != null
            && !variant.isQueuedForConversion()
            && retryLimitReached(variant)
            && Strings.isEmpty(variant.getPhysicalObjectKey())) {
            // The conversion has failed - signal that to the client. We use a handled exception here, as the problem
            // has already been logged...
            throwExhaustedConversionAttemptsException(blob.getBlobKey(), variantName);
        }
        return variant;
    }

    private void throwExhaustedConversionAttemptsException(String blobKey, String variantName) {
        throw Exceptions.handle()
                        .to(StorageUtils.LOG)
                        .withSystemErrorMessage(
                                "Layer2: Exhausted conversion attempts for variant '%s' and blobKey '%s'",
                                variantName,
                                blobKey)
                        .handle();
    }

    /**
     * Actually attempts to look up and create the requested variant when none was found.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param retries     the number of retries left
     * @return the variant for the given blob with the given name or null if no such variant exists
     * @throws Exception in case of an error while searching or creating the variant
     */
    @Nullable
    @SuppressWarnings("java:S3776")
    @Explain("This is a complex beast, but we rather keep the whole logic in one place.")
    private V attemptToFindOrCreateVariant(B blob, String variantName, int retries) throws Exception {
        // Always try to fetch the variant first, which could be present due to a previous conversion attempt
        // or a conversion has been created from another thread or node...
        V variant = null;
        try {
            variant = tryFetchVariant(blob, variantName);
        } catch (HandledException exception) {
            // The conversion ultimately failed. Invoke the handlers and re-throw the exception...
            failedVariantHandlers.forEach(handler -> handler.handle(exception, blob.getBlobKey(), variantName));
            throw exception;
        }

        if (variant != null && Strings.isFilled(variant.getPhysicalObjectKey())) {
            // We hit the nail on the head - we found a variant which has successfully been converted already.
            // -> use it
            return variant;
        }

        if (variant == null) {
            if (conversionEnabled) {
                // No variant is present, therefore we spawn a thread which will create it and start a conversion
                // pipeline
                if (tryCreateVariantAsync(blob, variantName)) {
                    // We successfully created a variant and forked a conversion... Await its result...
                    return awaitConversionResultAndRetryToFindVariant(blob, variantName, retries);
                } else {
                    // An optimistic lock error occurred (another thread or node attempted the same). So we back up,
                    // wait a short and random amount of time and retry...
                    return retryFindVariant(blob, variantName, retries);
                }
            } else {
                // No variant is present and no conversion is possible -> give up
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 2: Failed to create a conversion for %s to %s: Conversion is disabled on this node!",
                                        blob.getBlobKey(),
                                        variantName)
                                .handle();
            }
        }

        if (!shouldRetryConversion(variant)) {
            // A variant exists but didn't yield a usable result yet - try to wait and retry...
            return awaitConversionResultAndRetryToFindVariant(blob, variantName, retries);
        }

        if (conversionEnabled && !retryLimitReached(variant)) {
            // A variant exists, and we should re-try to create it...
            if (markConversionAttempt(variant)) {
                // We successfully marked this as "in conversion" -> fork a conversion task in parallel
                invokeConversionPipelineAsync(blob, null, variant);
                return awaitConversionResultAndRetryToFindVariant(blob, variantName, retries);
            } else {
                // An optimistic lock error occurred (another thread or node attempted the same). So we back up,
                // wait a short and random amount of time and retry...
                return retryFindVariant(blob, variantName, retries);
            }
        }

        // The variant is still being queued for conversion but didn't finish in time. We return null here,
        // so that we send an appropriate response to the client so that the browser might perform a retry...
        //
        // Note the check for "no conversion queued and also no result present" aka "conversion failed permanently"
        // is handled in the beginning of this method...
        return null;
    }

    /**
     * Handles the retry (after an optimistic lock error) path of
     * {@link #attemptToFindOrCreateVariant(Blob, String, int)}.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param retries     the number of retries left
     * @return either the result of the next attempt or <tt>null</tt> if we ran out of retries
     * @throws Exception if case of any error when performing the next attempt
     */
    private V retryFindVariant(B blob, String variantName, int retries) throws Exception {
        // An optimistic lock error occurred (another thread or node attempted the same). So we back up,
        // wait a short and random amount of time and retry...
        Wait.randomMillis(0, 150);
        return attemptToFindOrCreateVariant(blob, variantName, retries - 1);
    }

    /**
     * Handles the retry (when waiting for a conversion result) path of
     * {@link #attemptToFindOrCreateVariant(Blob, String, int)}.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param retries     the number of retries left
     * @return either the result of the next attempt or <tt>null</tt> if we ran out of retries
     * @throws Exception if case of any error when performing the next attempt
     */
    private V awaitConversionResultAndRetryToFindVariant(B blob, String variantName, int retries) throws Exception {
        if (retries == 0) {
            return null;
        }

        // Give the conversion pipeline some time to perform the conversion. Note that we fix the number of retries
        // here as no more optimistic lock problems can occur - we simply have to wait for the conversion to finish...
        Wait.millis((int) conversionRetryDelay.toMillis());
        return attemptToFindOrCreateVariant(blob, variantName, Math.min(retries - 1, retries));
    }

    /**
     * Tries to find the requested and converted variant in the database.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @return the variant or <tt>null</tt> if no matching variant exists
     */
    @Nullable
    protected abstract V findCompletedVariant(B blob, String variantName);

    /**
     * Tries to find the requested variant in the database.
     * <p>
     * Note: This will also return variants where the conversion is not completed yet.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @return the variant or <tt>null</tt> if no matching variant exists
     */
    @Nullable
    protected abstract V findAnyVariant(B blob, String variantName);

    /**
     * Marks the variant as queued for conversion.
     *
     * @param variant the variant to mark
     * @return <tt>true</tt> if the variant was successfully marked or <tt>false</tt> if another node / thread
     * updated the variant in the meanwhile
     * @throws Exception in case of an error when marking the variant as queued
     */
    protected abstract boolean markConversionAttempt(V variant) throws Exception;

    /**
     * Spawns a thread which will actually invoke the appropriate conversion pipeline.
     *
     * @param blob      the blob for which the variant is to be created
     * @param inputFile the file handle holding the file to use as input for the conversion
     * @param variant   the variant to generate
     * @return a future holding the conversion process
     */
    private Future invokeConversionPipelineAsync(B blob, FileHandle inputFile, V variant) {
        ConversionProcess conversionProcess = new ConversionProcess(blob, variant.getVariantName());
        if (inputFile != null) {
            conversionProcess.withInputFile(inputFile.getFile());
        }

        Future future = conversionEngine.performConversion(conversionProcess);
        future.onSuccess(ignored -> {
            try (FileHandle automaticHandle = conversionProcess.getResultFileHandle()) {
                String physicalKey = keyGenerator.generateId();
                conversionProcess.upload(() -> getPhysicalSpace().upload(physicalKey, automaticHandle.getFile()));

                markConversionSuccess(variant, physicalKey, conversionProcess);
                eventRecorder.record(new BlobConversionEvent().withConversionProcess(conversionProcess)
                                                              .withOutputFile(automaticHandle));
            }
        }).onFailure(conversionException -> {
            markConversionFailure(variant, conversionProcess);
            eventRecorder.record(new BlobConversionEvent().withConversionProcess(conversionProcess)
                                                          .withConversionError(conversionException));
        });
        return future;
    }

    /**
     * Marks the variant as successfully generated by supplying a physical object which contains the result.
     *
     * @param variant           the variant to update
     * @param physicalKey       the physical object which contains the new data
     * @param conversionProcess the process to determine the metadata from
     */
    protected abstract void markConversionSuccess(V variant, String physicalKey, ConversionProcess conversionProcess);

    /**
     * Records a failed conversion attempt for the given variant.
     *
     * @param variant the variant to record the failed attempt for
     */
    protected abstract void markConversionFailure(V variant, ConversionProcess conversionProcess);

    /**
     * Determines if another conversion of the variant should be attempted.
     * <p>
     * We do want to start a conversion if no conversion is currently queued, or is killed but has been stuck
     * for a while.
     *
     * @param variant the variant to check
     * @return <tt>true</tt> if another conversion should be triggered, <tt>false</tt> otherwise
     */
    private boolean shouldRetryConversion(V variant) {
        return !variant.isQueuedForConversion()
               || Duration.between(variant.getLastConversionAttempt(), LocalDateTime.now())
                          .compareTo(hangingConversionRetryInterval) > 0;
    }

    /**
     * Determines if the conversion of a variant has finally failed.
     *
     * @param variant the variant to check
     * @return <tt>true</tt> if the conversion has finally failed and no further conversions should be attempted,
     * <tt>false</tt> otherwise
     */
    private boolean retryLimitReached(V variant) {
        return variant.getNumAttempts() >= VARIANT_MAX_CONVERSION_ATTEMPTS;
    }

    /**
     * Tries to create the requested variant asynchronously if the variant does not exist already.
     *
     * @param blob        the blob for which the variant is to be created
     * @param variantName the variant to generate
     * @return <tt>true</tt> if the variant was created and a conversion has been forked, <tt>false</tt>
     * if the variant existed already...
     */
    private boolean tryCreateVariantAsync(B blob, String variantName) {
        V variant = createVariant(blob, variantName);

        if (detectAndRemoveDuplicateVariant(variant, blob, variantName)) {
            return false;
        } else {
            invokeConversionPipelineAsync(blob, null, variant);
            return true;
        }
    }

    /**
     * Tries to create the requested variant if the variant does not exist already.
     *
     * @param blob        the blob for which the variant is to be created
     * @param inputFile   the file handle holding the file to use as input for the conversion
     * @param variantName the variant to generate
     * @param retries     the number of retries left
     * @return a future holding the conversion process
     */
    private Future tryCreateVariant(B blob, FileHandle inputFile, String variantName, int retries) {
        if (retries == 0) {
            Future future = new Future();
            future.fail(new IllegalStateException(Strings.apply(
                    "Failed to execute an optimistic locked update after %s retries",
                    maxOptimisticLockAttempts)));
            return future;
        }

        V variant = tryFetchVariant(blob, variantName);

        if (variant != null && Strings.isFilled(variant.getPhysicalObjectKey())) {
            return new Future().success();
        }

        if (conversionEnabled) {
            if (variant == null) {
                variant = createVariant(blob, variantName);
            }

            if (detectAndRemoveDuplicateVariant(variant, blob, variantName)) {
                // An optimistic lock error occurred (another thread or node attempted the same). So we back up,
                // wait a short and random amount of time and retry...
                Wait.randomMillis(0, 150);
                // A collision was detected and the given variant was removed, therefore we need to create the variant again.
                return tryCreateVariant(blob, inputFile, variantName, retries - 1);
            } else {
                return invokeConversionPipelineAsync(blob, inputFile, variant);
            }
        } else {
            // No variant is present and no conversion is possible -> give up
            Future future = new Future();
            future.fail(Exceptions.handle()
                                  .to(StorageUtils.LOG)
                                  .withSystemErrorMessage(
                                          "Layer 2: Failed to create a conversion for %s to %s: Conversion is disabled on this node!",
                                          blob.getBlobKey(),
                                          variantName)
                                  .handle());
            return future;
        }
    }

    /**
     * Tries to create the requested variant if the variant does not exist already.
     *
     * @param blob        the blob for which the variant is to be created
     * @param variantName the variant to generate
     * @return a future holding the conversion process
     */
    public Future tryCreateVariant(B blob, String variantName) {
        return tryCreateVariant(blob, null, variantName, maxOptimisticLockAttempts);
    }

    /**
     * Tries to create the requested variant if the variant does not exist already.
     *
     * @param blob        the blob for which the variant is to be created
     * @param inputFile   the file handle holding the file to use as input for the conversion
     * @param variantName the variant to generate
     * @return a future holding the conversion process
     */
    public Future tryCreateVariant(B blob, FileHandle inputFile, String variantName) {
        return tryCreateVariant(blob, inputFile, variantName, maxOptimisticLockAttempts);
    }

    /**
     * Creates the required database object for the given variant.
     *
     * @param blob        the blob for which the variant is to be created
     * @param variantName the variant to generate
     * @return the newly created database object
     */
    protected abstract V createVariant(B blob, String variantName);

    /**
     * Creates the required database object for the given variant and physical object key.
     *
     * @param blob              the blob for which the variant is to be created
     * @param variantName       the variant to generate
     * @param physicalObjectKey the physical object to set
     * @return the newly created database object
     */
    protected abstract V createVariant(B blob, String variantName, String physicalObjectKey, long size);

    /**
     * Detects if the variant is still unique.
     * <p>
     * Note that this is kind of a negative approach - if the method returns <tt>true</tt> a collision was detected
     * and the given variant was removed. The <tt>false</tt> case is the expected one which verified that the variant
     * is unique and good to go.
     *
     * @param variant     the variant to check
     * @param blob        the blob for which the variant was created
     * @param variantName the name of the variant
     * @return <tt>true</tt> if a collision was detected and removed, <tt>false</tt> if the variant is still unique
     */
    protected abstract boolean detectAndRemoveDuplicateVariant(V variant, B blob, String variantName);

    /**
     * Delivers the requested variant in a separate thread pool.
     * <p>
     * This enables us to generate the requested variant is it doesn't exist yet.
     *
     * @param blobKey  the blob to deliver
     * @param variant  the variant of the blob to deliver
     * @param response the response to populate
     */
    private void deliverAsync(String blobKey, String variant, Response response) {
        tasks.executor(EXECUTOR_STORAGE_CONVERSION_DELIVERY)
             .dropOnOverload(() -> response.notCached().error(HttpResponseStatus.TOO_MANY_REQUESTS))
             .fork(() -> {
                 if (!conversionEnabled) {
                     delegateConversion(blobKey, variant, response, MAX_CONVERSION_DELEGATE_ATTEMPTS);
                     return;
                 }

                 try {
                     Tuple<String, Boolean> physicalKey =
                             tryCreatePhysicalKey(blobKey, variant, isMarkedToWaitLonger(response));
                     if (physicalKey == null) {
                         response.notCached().error(HttpResponseStatus.SERVICE_UNAVAILABLE);
                     } else {
                         response.addHeader(HEADER_VARIANT_SOURCE,
                                            Boolean.TRUE.equals(physicalKey.getSecond()) ? "cache" : "computed");
                         getPhysicalSpace().deliver(response, physicalKey.getFirst(), false);
                     }
                 } catch (Exception _) {
                     response.notCached().error(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                 }
             });
    }

    /**
     * Used if the requested variant doesn't exist but the local node is not permitted to perform the conversion itself.
     * <p>
     * In this case, we attempt to tunnel the request to one of our known conversion servers - or if this fails or
     * if there is no conversion server available - we give up and respond with an error.
     *
     * @param blobKey  the blob to deliver
     * @param variant  the variant of the blob to deliver
     * @param response the response to populate
     */
    private void delegateConversion(String blobKey, String variant, Response response, int maxAttempts) {
        Optional<URL> url = determineDelegateConversionUrl(blobKey, variant, isMarkedToWaitLonger(response));

        if (url.isEmpty()) {
            response.error(HttpResponseStatus.NOT_FOUND);
            return;
        }

        response.addHeader(HEADER_VARIANT_SOURCE, "delegated");
        response.addHeader(HEADER_VARIANT_COMPUTER, url.get().getHost());
        if (maxAttempts > 1) {
            response.tunnel(url.get().toString(), errorCode -> {
                if (errorCode == HttpResponseStatus.INTERNAL_SERVER_ERROR.code() && conversionHosts.size() > 1) {
                    // Re-try conversion as the uplink conversion host might be down, but another is alive...
                    recordHostConnectivityIssue(url.get().getHost());
                    delegateConversion(blobKey, variant, response, maxAttempts - 1);
                } else {
                    response.error(HttpResponseStatus.valueOf(errorCode));
                }
            });
        } else {
            response.tunnel(url.get().toString());
        }
    }

    private boolean isMarkedToWaitLonger(Response response) {
        return Boolean.parseBoolean(response.getHeader(BlobDispatcher.HEADER_WAIT_LONGER));
    }

    private void recordHostConnectivityIssue(String host) {
        conversionHostLastConnectivityIssue.put(host, LocalDateTime.now());
        StorageUtils.LOG.WARN("Layer 2: Detected a connectivity issue for conversion host %s!", host);
    }

    /**
     * Generates an appropriate URL as URLBuilder would do which will then be handled by the BlobDispatcher of a
     * conversion server.
     * <p>
     * Note that this will be a call within the cluster, therefore http is fine (or even required as
     * we're behind the SSL offload server).
     *
     * @param blobKey the blob to deliver
     * @param variant the variant of the blob to deliver
     * @return the URL for requesting the variant from one of our conversion servers
     */
    @SuppressWarnings("HttpUrlsUsage")
    @Explain("These are cluster internal URLs and thus http is acceptable.")
    private Optional<URL> determineDelegateConversionUrl(String blobKey, String variant, boolean waitLonger) {
        if (conversionEnabled || conversionHosts.isEmpty()) {
            return Optional.empty();
        }

        String randomHost = pickRandomConversionHost(MAX_CONVERSION_DELEGATE_HOST_PICK_ATTEMPTS);

        String conversionUrl = "http://"
                               + randomHost
                               + BlobDispatcher.URI_PREFIX_TRAILED
                               + BlobDispatcher.FLAG_VIRTUAL
                               + "/"
                               + spaceName
                               + "/"
                               + utils.computeHash(blobKey + "-" + variant, 0)
                               + "/"
                               + variant
                               + "/"
                               + blobKey;

        if (waitLonger) {
            conversionUrl += Strings.apply("?%s=true", BlobDispatcher.WAIT_LONGER_PARAMETER);
        }

        try {
            return Optional.of(URI.create(conversionUrl).toURL());
        } catch (MalformedURLException exception) {
            throw Exceptions.handle(exception);
        }
    }

    private String pickRandomConversionHost(int remainingAttempts) {
        String host = conversionHosts.get(ThreadLocalRandom.current().nextInt(conversionHosts.size()));
        if (remainingAttempts <= 0) {
            return host;
        }
        if (conversionHosts.size() == 1) {
            return host;
        }

        LocalDateTime lastConnectivityError = conversionHostLastConnectivityIssue.get(host);
        if (lastConnectivityError == null) {
            return host;
        }
        Duration durationSinceLastConnectivityIssue = Duration.between(lastConnectivityError, LocalDateTime.now());
        if (durationSinceLastConnectivityIssue.compareTo(MAX_CONVERSION_HOST_BLACKLISTING) > 0) {
            conversionHostLastConnectivityIssue.remove(host);
            StorageUtils.LOG.INFO("Layer 2: Connectivity issue for conversion host %s expired", host);

            return host;
        }

        return pickRandomConversionHost(remainingAttempts - 1);
    }

    /**
     * Returns the base URL to use for this space.
     *
     * @return the base URL wrapped as optional, or an empty optional if the default base URL should be used.
     */
    public Optional<String> getBaseURL() {
        return Optional.ofNullable(baseUrl);
    }
}
