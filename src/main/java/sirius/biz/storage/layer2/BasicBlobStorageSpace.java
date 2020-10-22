/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.locks.Locks;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.layer2.variants.ConversionEngine;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.KeyGenerator;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Processor;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
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
    private static final int NUMBER_OF_ATTEMPTS_FOR_OPTIMISTIC_LOCKS = 5;

    /**
     * Specifies the interval (in minutes) after which a conversion is retried for a given blob variant.
     */
    private static final int VARIANT_CONVERSION_RETRY_INTERVAL_MINUTES = 45;

    /**
     * Specifies the maximal number of attempts to generate a variant.
     */
    private static final int VARIANT_MAX_CONVERSION_ATTEMPTS = 3;

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
     * Setting this to true makes the file system effectively case insensitive. Where as using false
     * makes it case sensitive.
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
     * Determines if touch tracking is active for this space.
     */
    private static final String CONFIG_KEY_TOUCH_TRACKING = "touchTracking";

    /**
     * Contains the name of the executor in which requests are moved which might be blocked while waiting for
     * a conversion to happen. We do not want to jam our main executor of the web server for this, therefore
     * a separator one is used.
     */
    private static final String EXECUTOR_STORAGE_CONVERSION_DELIVERY = "storage-conversion-delivery";

    @Part
    protected static ObjectStorage objectStorage;

    @Part
    protected static KeyGenerator keyGenerator;

    @Part
    protected static StorageUtils utils;

    @Part
    private static Locks locks;

    @Part
    protected static ConversionEngine conversionEngine;

    @Part
    protected static Tasks tasks;

    @Part
    protected static TouchWritebackLoop touchWritebackLoop;

    @ConfigValue("storage.layer2.conversion.enabled")
    protected static boolean conversionEnabled;

    @ConfigValue("storage.layer2.conversion.hosts")
    protected static List<String> conversionHosts;

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

    /**
     * Caches the {@link Blob blobs} that belong to certain paths
     */
    protected static Cache<String, Blob> blobByPathCache = CacheManager.createCoherentCache("storage-paths");

    protected final Extension config;
    protected final String description;
    protected String spaceName;
    protected String readPermission;
    protected String writePermission;
    protected String baseUrl;
    protected boolean useNormalizedNames;
    protected int retentionDays;
    protected boolean touchTracking;
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
        this.description = config.get(CONFIG_KEY_DESCRIPTION).getString();
        this.retentionDays = config.get(CONFIG_KEY_RETENTION_DAYS).asInt(0);
        this.touchTracking = config.get(CONFIG_KEY_TOUCH_TRACKING).asBoolean();
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

    /**
     * Provides the skeleton of most of the optimistic locking algorithms used by this class.
     * <p>
     * Some parts of this storage space need to fulfill some invariants (e.g. a filename is unique within a directory).
     * However, most of the time, these won't be falsified, even in concurrent use. Therefore we use an optimistic
     * locking approach. This is essentially a fastpath (create a data object) without any locking or other protection
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
        int attempts = NUMBER_OF_ATTEMPTS_FOR_OPTIMISTIC_LOCKS;
        try {
            while (attempts-- > 0) {
                R result = tryFindOrCreate(lookup, factory, correctnessTest, commit, rollback);
                if (result != null) {
                    return result;
                }

                Wait.randomMillis(50, 250);
            }

            throw errorHandler.apply(new IllegalStateException(Strings.apply(
                    "Failed to execute an optimistic locked update after %s retries",
                    NUMBER_OF_ATTEMPTS_FOR_OPTIMISTIC_LOCKS)));
        } catch (Exception e) {
            throw errorHandler.apply(e);
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
        if (Strings.isEmpty(path)) {
            return Optional.empty();
        }

        return Optional.ofNullable(blobByPathCache.get(determinePathCacheKey(tenantId, path),
                                                       ignored -> fetchByPath(tenantId, path)));
    }

    @Nonnull
    private String determinePathCacheKey(String tenantId, String path) {
        return spaceName + "-" + tenantId + "-" + ensureRelativePath(path);
    }

    protected Blob fetchByPath(String tenantId, @Nonnull String path) {
        String[] parts = ensureRelativePath(path).split("/");
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

    protected String ensureRelativePath(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }

        return path;
    }

    @Override
    public Optional<? extends Blob> findByPath(String path) {
        return findByPath(UserContext.getCurrentUser().getTenantId(), path);
    }

    @Override
    public Blob findOrCreateByPath(String tenantId, String path) {
        if (Strings.isEmpty(path)) {
            throw new IllegalArgumentException("An empty path was provided!");
        }

        String key = determinePathCacheKey(tenantId, path);
        Blob blob = blobByPathCache.get(key);
        if (blob == null) {
            blobByPathCache.remove(key);
            blob = fetchOrCreateByPath(tenantId, path);
        }
        return blob;
    }

    protected Blob fetchOrCreateByPath(String tenantId, @Nonnull String path) {
        String[] parts = ensureRelativePath(path).split("/");
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
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the attached blob for space '%s' and reference '%s' with filename '%s%'.",
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
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the child directory for space '%s' and parent directory '%s' with name '%s%'.",
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
                                                                 .withSystemErrorMessage(
                                                                         "Layer 2: Failed to create the child blob for space '%s' and parent directory '%s' with name '%s%'.",
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

        blobByPathCache.clear();
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

        blobByPathCache.clear();
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
        if (blob.getParent().hasChildNamed(newName)) {
            throw Exceptions.createHandled().withNLSKey("BasicBlobStorageSpace.cannotRenameDuplicateName").handle();
        }

        updateBlobName(blob, newName);

        blobKeyToFilenameCache.remove(blob.getBlobKey());
        blobByPathCache.clear();
    }

    /**
     * Effectively updates the blob name after all checks have been passed.
     *
     * @param blob    the blob to rename
     * @param newName the new name to use
     */
    protected abstract void updateBlobName(B blob, String newName);

    /**
     * Performs a download / fetch of the given blob to make its data locally accessible.
     *
     * @param blob the blob to fetch the data for
     * @return a file handle which makes the blob data accessible or an empty optional if no data was present.
     * Note that the {@link FileHandle} must be closed once the data has been processed to ensure proper cleanup.
     */
    public Optional<FileHandle> download(Blob blob) {
        if (Strings.isEmpty(blob.getPhysicalObjectKey())) {
            return Optional.empty();
        }

        touch(blob.getBlobKey());
        return getPhysicalSpace().download(blob.getPhysicalObjectKey());
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
            getPhysicalSpace().upload(nextPhysicalId, file);
            blobKeyToPhysicalCache.remove(buildPhysicalKey(blob.getBlobKey(), URLBuilder.VARIANT_RAW));
            Optional<String> previousPhysicalId = updateBlob(blob, nextPhysicalId, file.length(), filename);
            if (previousPhysicalId.isPresent()) {
                purgeBlobVariants(blob);
                getPhysicalSpace().delete(previousPhysicalId.get());
            }

            if (Strings.isFilled(filename)) {
                blobByPathCache.clear();
            }
        } catch (Exception e) {
            try {
                getPhysicalSpace().delete(nextPhysicalId);
            } catch (Exception ex) {
                Exceptions.ignore(ex);
            }

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
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
            getPhysicalSpace().upload(nextPhysicalId, data, contentLength);
            blobKeyToPhysicalCache.remove(buildPhysicalKey(blob.getBlobKey(), URLBuilder.VARIANT_RAW));
            Optional<String> previousPhysicalId = updateBlob(blob, nextPhysicalId, contentLength, filename);
            if (previousPhysicalId.isPresent()) {
                purgeBlobVariants(blob);
                getPhysicalSpace().delete(previousPhysicalId.get());
            }

            if (Strings.isFilled(filename)) {
                blobByPathCache.clear();
            }
        } catch (Exception e) {
            try {
                getPhysicalSpace().delete(nextPhysicalId);
            } catch (Exception ex) {
                Exceptions.ignore(ex);
            }

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2: Cannot update the contents of %s in %s: %s (%s)",
                                                    blob.getBlobKey(),
                                                    spaceName)
                            .handle();
        }
    }

    private void purgeBlobVariants(B blob) {
        blob.fetchVariants().forEach(blobVariant -> {
            blobVariant.delete();
            blobKeyToPhysicalCache.remove(buildPhysicalKey(blob.getBlobKey(), blobVariant.getVariantName()));
        });
    }

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
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
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
    public void deliver(String blobKey, String variant, Response response) {
        touch(blobKey);

        String physicalKey = resolvePhysicalKey(blobKey, variant, true);
        if (physicalKey != null) {
            getPhysicalSpace().deliver(response, physicalKey);
        } else {
            deliverAsync(blobKey, variant, response);
        }
    }

    @Override
    public void deliverPhysical(@Nullable String blobKey, @Nonnull String physicalKey, @Nonnull Response response) {
        touch(blobKey);
        getPhysicalSpace().deliver(response, physicalKey);
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
     * @param nonblocking <tt>true</tt> to directly respond and rather return <tt>null</tt> instead of generating the
     *                    requested variant on demand, <tt>false</tt> to generate the variant if possible.
     * @return the physical key or <tt>null</tt> if the appropriate variant wasn't found
     */
    @Nullable
    protected String resolvePhysicalKey(String blobKey, String variantName, boolean nonblocking) {
        String cacheKey = buildPhysicalKey(blobKey, variantName);
        String cachedPhysicalKey = blobKeyToPhysicalCache.get(cacheKey);
        if (Strings.isFilled(cachedPhysicalKey)) {
            return cachedPhysicalKey;
        }

        String physicalKey = lookupPhysicalKey(blobKey, variantName, nonblocking);
        if (physicalKey != null) {
            blobKeyToPhysicalCache.put(cacheKey, physicalKey);
        }

        return physicalKey;
    }

    private String buildPhysicalKey(String blobKey, String variantName) {
        return spaceName + "-" + blobKey + "-" + variantName;
    }

    /**
     * Performs the actual lookup of the physical key for the given blob and variant.
     *
     * @param blobKey     the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param nonblocking <tt>true</tt> to directly respond and rather return <tt>null</tt> instead of generating the
     *                    requested variant on demand, <tt>false</tt> to generate the variant if possible.
     * @return the physical key or <tt>null</tt> if the appropriate variant wasn't found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    protected String lookupPhysicalKey(String blobKey, @Nonnull String variantName, boolean nonblocking) {
        B blob = (B) findByBlobKey(blobKey).orElse(null);
        if (blob == null) {
            return null;
        }

        if (URLBuilder.VARIANT_RAW.equals(variantName)) {
            return blob.getPhysicalObjectKey();
        }

        if (!conversionEngine.isKnownVariant(variantName)) {
            return null;
        }

        V variant = findOrCreateVariant(blob, variantName, nonblocking);

        if (variant == null || Strings.isEmpty(variant.getPhysicalObjectKey())) {
            return null;
        } else {
            return variant.getPhysicalObjectKey();
        }
    }

    /**
     * Tries to either find or create the requested variant for the given blob.
     * <p>
     * Note that there this is an recursive optimistic locking algorithm at work where
     * {@link #attemptToFindOrCreateVariant(Blob, String, boolean, int)} and
     * {@link #retryToFindOrCreateVariant(Blob, String, int)} build the "loop".
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param nonblocking <tt>true</tt> to directly respond and rather return <tt>null</tt> instead of generating the
     *                    requested variant on demand, <tt>false</tt> to generate the variant if possible.
     * @return the variant for the given blob with the given name or null if no such variant exists
     */
    @Nullable
    protected V findOrCreateVariant(B blob, String variantName, boolean nonblocking) {
        try {
            return attemptToFindOrCreateVariant(blob,
                                                variantName,
                                                nonblocking,
                                                NUMBER_OF_ATTEMPTS_FOR_OPTIMISTIC_LOCKS - 1);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 2: Failed to find or create the variant %s of %s in %s: %s (%s)",
                                    variantName,
                                    blob.getBlobKey(),
                                    spaceName)
                            .handle();
        }
    }

    /**
     * Actually attempts to either lookup or create the requested variant.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param nonblocking <tt>true</tt> to directly respond and rather return <tt>null</tt> instead of generating the
     *                    requested variant on demand, <tt>false</tt> to generate the variant if possible.
     * @param retries     the number of retries left
     * @return the variant for the given blob with the given name or null if no such variant exists
     * @throws Exception in case of an error while searching or creating the variant
     */
    @Nullable
    private V attemptToFindOrCreateVariant(B blob, String variantName, boolean nonblocking, int retries)
            throws Exception {
        V variant = findVariant(blob, variantName);
        if (variant != null && Strings.isFilled(variant.getPhysicalObjectKey())) {
            // We hit the nail on the head - we found a variant which has successfully been converted already.
            // -> use it
            return variant;
        }

        if (nonblocking) {
            // We didn't find anything and the caller doesn't permit to block and wait for either our node or
            // another to create the variant -> abort with null
            return null;
        }

        if (variant == null) {
            if (conversionEnabled) {
                // No variant is present, therefore we spawn a thread which will create it and start a conversion
                // pipeline
                tryCreateVariantAsync(blob, variantName);

                // In parallel we park this thread and then retry if we can already use this variant. As this is
                // an optimistic locking algorithm anyway which can wait on another node / thread to create the variant
                // we can also use the same approach if we ourself triggered the conversion task....
                return retryToFindOrCreateVariant(blob, variantName, retries);
            } else {
                // No variant is present and no conversion is possible -> give up
                return null;
            }
        }

        if (!shouldRetryConversion(variant)) {
            // A variant exists but didn't yield a useable result yet - try to wait and retry...
            return retryToFindOrCreateVariant(blob, variantName, retries);
        }

        if (conversionEnabled && !retryLimitReached(variant)) {
            // A variant exists and we should re-try to create it...
            if (markConversionAttempt(variant)) {
                // We successfully marked this as "in conversion" -> fork a conversion task in parallel
                invokeConversionPipelineAsync(blob, variant);
            }

            // ...and no we wait on either our or another conversion task to finish so that we can use
            // the result
            return retryToFindOrCreateVariant(blob, variantName, retries);
        }

        // A variant exists but we either gave up on trying to create the actual physical object or we're simply
        // not capable of doing so...
        return null;
    }

    /**
     * Handles the retry path of {@link #attemptToFindOrCreateVariant(Blob, String, boolean, int)}.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @param retries     the number of retries left
     * @return either the result of the next attempt or <tt>null</tt> if we ran out of retries
     * @throws Exception if case of any error when performing the next attempt
     */
    private V retryToFindOrCreateVariant(B blob, String variantName, int retries) throws Exception {
        if (retries == 0) {
            return null;
        }
        Wait.randomMillis(100, 500);
        return attemptToFindOrCreateVariant(blob, variantName, false, retries - 1);
    }

    /**
     * Tries to find the requeted variant in the database.
     *
     * @param blob        the blob for which the variant is to be resolved
     * @param variantName the variant of the blob to find
     * @return the variant or <tt>null</tt> if no matching variant exists
     */
    @Nullable
    protected abstract V findVariant(B blob, String variantName);

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
     * @param blob    the blob for which the variant is to be created
     * @param variant the variant to generate
     */
    private void invokeConversionPipelineAsync(B blob, V variant) {
        conversionEngine.performConversion(blob, variant.getVariantName()).onSuccess(handle -> {
            try (FileHandle automaticHandle = handle) {
                String physicalKey = keyGenerator.generateId();
                getPhysicalSpace().upload(physicalKey, automaticHandle.getFile());
                markConversionSuccess(variant, physicalKey, automaticHandle.getFile().length());
            }
        }).onFailure(e -> {
            Exceptions.handle()
                      .error(e)
                      .to(StorageUtils.LOG)
                      .withSystemErrorMessage("Layer 2/Conversion: Failed to create %s (%s) of %s (%s): %s (%s)",
                                              variant.getVariantName(),
                                              variant.getIdAsString(),
                                              blob.getBlobKey(),
                                              blob.getFilename())
                      .handle();

            markConversionFailure(variant);
        });
    }

    /**
     * Marks the variant as successfully generated by supplying a physical object which contains the result.
     *
     * @param variant     the variant to update
     * @param physicalKey the physical object which contains the new data
     * @param size        the size of the data sored in the physical object
     */
    protected abstract void markConversionSuccess(V variant, String physicalKey, long size);

    /**
     * Records a failed conversion attempt for the given variant.
     *
     * @param variant the variant to record the failed attempt for
     */
    protected abstract void markConversionFailure(V variant);

    /**
     * Determines if another conversion of the variant should be attempted.
     *
     * @param variant the variant to check
     * @return <tt>true</tt> if another conversion should be triggered, <tt>false</tt> otherwise
     */
    private boolean shouldRetryConversion(V variant) {
        return !variant.isQueuedForConversion()
               || Duration.between(variant.getLastConversionAttempt(), LocalDateTime.now()).toMinutes()
                  > VARIANT_CONVERSION_RETRY_INTERVAL_MINUTES;
    }

    /**
     * Determines if the conversion of a variant has finally failed.
     *
     * @param variant the variant to check
     * @return <tt>true</tt> if the conversion has finally failed and not further conversions should be attempted,
     * <tt>false</tt> otherwise
     */
    private boolean retryLimitReached(V variant) {
        return variant.getNumAttempts() >= VARIANT_MAX_CONVERSION_ATTEMPTS;
    }

    /**
     * Tries to create and the asynchronically generated the requested variant.
     *
     * @param blob        the blob for which the variant is to be created
     * @param variantName the variant to generate
     */
    private void tryCreateVariantAsync(B blob, String variantName) {
        V variant = createVariant(blob, variantName);

        if (!detectAndRemoveDuplicateVariant(variant, blob, variantName)) {
            invokeConversionPipelineAsync(blob, variant);
        }
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
             .dropOnOverload(() -> response.error(HttpResponseStatus.TOO_MANY_REQUESTS))
             .fork(() -> {
                 if (conversionEnabled) {
                     String physicalKey = resolvePhysicalKey(blobKey, variant, false);
                     if (physicalKey != null) {
                         getPhysicalSpace().deliver(response, physicalKey);
                     } else {
                         response.error(HttpResponseStatus.NOT_FOUND);
                     }
                 } else {
                     delegateConversion(blobKey, variant, response);
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
    private void delegateConversion(String blobKey, @Nullable String variant, Response response) {
        if (conversionEnabled || conversionHosts.isEmpty()) {
            response.error(HttpResponseStatus.NOT_FOUND);
            return;
        }

        String conversionHost = conversionHosts.get(ThreadLocalRandom.current().nextInt(conversionHosts.size()));
        // Generates an appropriate URL as URLBuilder would do which will then handled by the BlobDispatcher
        // of the conversion server. Note that this will be a call within the cluster, therefore http is fine
        // (or even required as we're behind the SSL offload server).
        String conversionUrl = "http://"
                               + conversionHost
                               + BlobDispatcher.URI_PREFIX
                               + "/"
                               + BlobDispatcher.FLAG_VIRTUAL
                               + "/"
                               + spaceName
                               + "/"
                               + utils.computeHash(blobKey + "-" + variant, 0)
                               + "/"
                               + variant
                               + "/"
                               + blobKey;

        response.tunnel(conversionUrl);
    }

    /**
     * Returns the base URL to use for this space.
     *
     * @return the base URL wrapped as optional or an empty optional if the default base URL should be used.
     */
    public Optional<String> getBaseURL() {
        return Optional.ofNullable(baseUrl);
    }
}
