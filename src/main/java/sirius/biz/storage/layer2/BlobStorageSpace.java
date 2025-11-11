/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Hasher;
import sirius.web.http.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a layer 2 storage space which manages {@link Blob blobs} and {@link Directory directories}.
 */
public interface BlobStorageSpace {

    /**
     * Returns the name of this blog storage space.
     *
     * @return the name of this space
     */
    String getName();

    /**
     * Returns a short description of what use case of this storage space.
     * <p>
     * Most probably this only needs to return a non-empty value if {@link #isBrowsable()} return true.
     *
     * @return a short description of the storage space.
     */
    @Nullable
    String getDescription();

    /**
     * Provides the <tt>academyTrackId</tt> if present in the config block.
     *
     * @return the track id of the video {@link sirius.biz.tycho.academy.OnboardingEngine academy} to show when
     * browsing this space.
     */
    @Nullable
    String getAcademyVideoTrackId();

    /**
     * Provides the <tt>academyVideoCode</tt> if present in the config block.
     *
     * @return the code of the video within the {@link sirius.biz.tycho.academy.OnboardingEngine academy} to show when
     * browsing this space.
     */
    @Nullable
    String getAcademyVideoCode();

    /**
     * Provides the <tt>kba</tt> if present in the config block.
     *
     * @return the code of the {@link sirius.biz.tycho.kb.KnowledgeBase kba} to show when browsing this space.
     */
    @Nullable
    String getKnowledgeBaseArticleCode();

    /**
     * Returns the associated layer 1 space which actually stores the data.
     *
     * @return the associated physical storage space
     */
    ObjectStorageSpace getPhysicalSpace();

    /**
     * Tries to find the blob with the given key.
     *
     * @param key the blob key to search by
     * @return the blob in this space with the given key wrapped as optional or an empty optional if no such
     * blob exists.
     */
    Optional<? extends Blob> findByBlobKey(String key);

    /**
     * Returns the root directory for the given tenant.
     * <p>
     * Note that {@link Tenants#getSystemTenantId()} can be used for system related files (as long as the
     * <tt>tenants</tt> framework is used).
     *
     * @param tenantId the tenant to determine the directory for
     * @return the root directory for the given tenant
     * @throws sirius.kernel.health.HandledException if the space isn't {@link #isBrowsable() browsable}
     */
    Directory getRoot(String tenantId);

    /**
     * Resolves the given path into a blob.
     * <p>
     * Note that {@link Tenants#getSystemTenantId()} can be used for system related files (as long as the
     * <tt>tenants</tt> framework is used).
     *
     * @param tenantId the tenant which owns the directory structure to search in
     * @param path     the path to resolve (may start with a "/" but not with the space name itself)
     * @return the blob wrapped as optional or an empty optional if no matching blob was found
     */
    Optional<? extends Blob> findByPath(String tenantId, String path);

    /**
     * Uses the current tenant and the given path to resolve this into an existing blob.
     *
     * @param path the path to resolve (may start with a "/" but not with the space name itself)
     * @return the blob wrapped as optional or an empty optional if no matching blob was found
     */
    Optional<? extends Blob> findByPath(String path);

    /**
     * Resolves or creates the blob with the given path.
     * <p>
     * Note that all intermediate directories will be auto-created.
     * <p>
     * Note that {@link Tenants#getSystemTenantId()} can be used for system related files (as long as the
     * <tt>tenants</tt> framework is used).
     *
     * @param tenantId the tenant which owns the directory structure to search in
     * @param path     the path to resolve (may start with a "/" but not with the space name itself)
     * @return the found or newly created blob
     */
    Blob findOrCreateByPath(String tenantId, String path);

    /**
     * Uses the current tenant and the given path to resolve or create an appropriate blob.
     * <p>
     * Note that all intermediate directories will be auto-created.
     *
     * @param path the path to resolve (may start with a "/" but not with the space name itself)
     * @return the found or newly created blob
     */
    Blob findOrCreateByPath(String path);

    /**
     * Creates a new temporary blob to be used in a {@link BlobHardRef}.
     * <p>
     * Such objects will be automatically deleted if the referencing entity is
     * deleted. It is made permanent as soon as the referencing entity is saved, otherwise it will be deleted
     * automatically.
     * <p>
     * Note that there is almost no use-case to directly call this method outside of {@link BlobHardRef}.
     *
     * @return the newly created temporary blob. To make this blob permanent, it has to be stored in a
     * {@link BlobHardRef} and the referencing entity has to be persisted.
     */
    Blob createTemporaryBlob();

    /**
     * Creates a new temporary blob.
     * <p>
     * Note that this blob will be automatically deleted if it isn't marked as permanent using
     * {@link #markAsUsed(Blob)}.
     * <p>
     * Note that calling this method should be a rare edge-case as normally blobs are either attached to
     * entities using {@link BlobSoftRef}, {@link BlobHardRef}, {@link BlobContainer} or the directory API
     * (most probably via the Level 3 VFS API). One such case is the {@link sirius.biz.jobs.JobsRoot} which places
     * temporary files in the <b>tmp</b> space - these files are not listed in the VFS UI but can be resolved if
     * the exact path is given (see {@link sirius.biz.storage.layer3.TmpRoot}).
     *
     * @param tenantId the tenant id to associate this blob with
     * @return the newly created temporary blob which is associated with the given tenant.
     * @see #markAsUsed(Blob)
     * @see sirius.biz.storage.layer3.TmpRoot
     */
    Blob createTemporaryBlob(String tenantId);

    /**
     * Fetches a blob attached to an entity via a {@link BlobContainer}.
     *
     * @param referencingEntity the referencing entity
     * @param filename          the filename to lookup
     * @return the matching blob wrapped as optional or an empty optional if no matching blob was found
     */
    Optional<? extends Blob> findAttachedBlobByName(String referencingEntity, String filename);

    /**
     * Fetches or creates a blob attached to an entity via a {@link BlobContainer}.
     *
     * @param referencingEntity the referencing entity
     * @param filename          the filename to lookup
     * @return the matching blob which was either found or newly created
     */
    Blob findOrCreateAttachedBlobByName(String referencingEntity, String filename);

    /**
     * Lists all blobs attached to an entity via a {@link BlobContainer}.
     *
     * @param referencingEntity the referencing entity
     * @return a list of all attached blobs
     */
    List<? extends Blob> findAttachedBlobs(String referencingEntity);

    /**
     * Deletes all blobs attached to an entity via a {@link BlobContainer}.
     *
     * @param referencingEntity the referencing entity
     */
    void deleteAttachedBlobs(String referencingEntity);

    /**
     * Attaches the given temporary blob to the given entity.
     * <p>
     * This is used by the {@link BlobContainer} to reference arbitrary blobs from an entity. This is mainly used,
     * if the blobs are not referenced by their filename.
     *
     * @param objectKey         the blob to reference
     * @param referencingEntity the unique name of the entity
     */
    void attachTemporaryBlob(String objectKey, String referencingEntity);

    /**
     * Fetches a blob attached to an entity via a {@link BlobContainer}.
     *
     * @param referencingEntity the referencing entity
     * @param blobKey           the blob key to lookup
     * @return the matching blob wrapped as optional or an empty optional if no matching blob was found
     */
    Optional<? extends Blob> findAttachedBlobByKey(String referencingEntity, String blobKey);

    /**
     * Attaches the given existing blob to the given entity and designator.
     * <p>
     * This is used by the {@link BlobReferenceContainer} to reference existing (most probably visible blobs)
     * from an entity.
     *
     * @param objectKey           the blob to reference
     * @param referencingEntity   the unique name of the entity
     * @param referenceDesignator the type in {@link BlobReferenceContainer}
     */
    void attachBlobByType(String objectKey, String referencingEntity, String referenceDesignator);

    /**
     * Resolves the referenced blob for a {@link BlobReferenceContainer}.
     *
     * @param referencingEntity   the unique name of the entity
     * @param referenceDesignator the type used in {@link BlobReferenceContainer}
     * @return the referenced blob wrapped as optional or an empty optional if no blob was attached
     */
    Optional<? extends Blob> findAttachedBlobByType(String referencingEntity, String referenceDesignator);

    /**
     * Used by {@link BlobHardRef} when the referencing entity is persisted.
     * <p>
     * This will mark blobs created with {@link #createTemporaryBlob()} as permanent.
     *
     * @param referencingEntity   the unique name of the referencing entity
     * @param referenceDesignator the field designator of the referencing entity
     * @param blobKey             the object to mark as permanent
     * @throws sirius.kernel.health.HandledException if the blob is already referenced somewhere else
     * @see BlobHardRefProperty#onAfterDelete(Object)
     */
    void markAsUsed(String referencingEntity, String referenceDesignator, String blobKey);

    /**
     * Manually marks a blob as used.
     * <p>
     * This will mark blobs created with {@link #createTemporaryBlob(String)} as permanent.
     *
     * @param blob the blob to mark as used. Note that the entity itself is not necessarily updated.
     * @see sirius.biz.storage.layer3.TmpRoot
     */
    void markAsUsed(Blob blob);

    /**
     * Used by {@link BlobHardRef}  to delete all referenced blobs (most probably 1) for the given referencing entity
     * and field.
     *
     * @param referencingEntity   the unique name of the referencing entity
     * @param referenceDesignator the field designator of the referencing entity
     * @param blobKeyToSkip       can contain a blob key which will not be deleted. This will most probably the new field
     *                            value after an update. This greatly simplifies the update process as only the superfluous
     *                            blobs need to be deleted in a single call of this method.
     * @see BlobHardRefProperty#onAfterDelete(Object)
     */
    void deleteReferencedBlobs(String referencingEntity, String referenceDesignator, @Nullable String blobKeyToSkip);

    /**
     * Determines if this space is browsable (available as virtual file system in layer 3).
     *
     * @return <tt>true</tt> if this space is available as file system in layer 3, <tt>false</tt> otherwise
     * @see L3Uplink
     * @see sirius.biz.storage.layer3.VirtualFileSystem
     */
    boolean isBrowsable();

    /**
     * Determines if this space is readonly when browsing through it.
     *
     * @return <tt>true</tt> if this space is readonly, <tt>false</tt> otherwise
     */
    boolean isReadonly();

    /**
     * Downloads and provides the contents of the requested blob with the given variant.
     *
     * @param blobKey    the blob key of the blob to download
     * @param variant    the variant of the blob to download. Use {@link URLBuilder#VARIANT_RAW} to download the blob itself
     * @param waitLonger whether to wait longer for the conversion to complete
     * @return a handle to the given object wrapped as optional or an empty one if the object doesn't exist or couldn't
     * be converted
     */
    Optional<FileHandle> download(String blobKey, String variant, boolean waitLonger);

    /**
     * Resolves the filename of the given blob.
     *
     * @param blobKey the blob to look up the filename for
     * @return the filename if present or an empty optional if non was found
     */
    Optional<String> resolveFilename(@Nonnull String blobKey);

    /**
     * Delivers the requested blob to the given HTTP response.
     *
     * @param response              the response to populate
     * @param blobKey               the {@link Blob#getBlobKey()} of the {@link Blob} to deliver
     * @param variant               the variant to deliver. Use {@link URLBuilder#VARIANT_RAW} to deliver the blob itself
     * @param markCallAsLongRunning invoked if we know that the call will take longer than expected as we need to
     *                              invoke the {@link sirius.biz.storage.layer2.variants.ConversionEngine} first.
     */
    void deliver(@Nonnull String blobKey,
                 @Nonnull String variant,
                 @Nonnull Response response,
                 @Nullable Runnable markCallAsLongRunning);

    /**
     * Delivers the contents of the given blob by using the already known physicalKey.
     *
     * @param blobKey           the id of the blob to deliver (mostly for touch tracking)
     * @param physicalKey       the physical object to deliver
     * @param response          the response to populate
     * @param largeFileExpected determines that a very large file is expected
     */
    void deliverPhysical(@Nullable String blobKey,
                         @Nonnull String physicalKey,
                         @Nonnull Response response,
                         boolean largeFileExpected);

    /**
     * Performs some housekeeping and maintenance tasks.
     * <p>
     * This shouldn't be invoked manually as it is triggered via the {@link StorageCleanupTask}.
     */
    void runCleanup();

    /**
     * Determines if touch tracking is active for this space.
     *
     * @return <tt>true</tt> if touch tracking is active, <tt>false</tt> otherwise
     */
    boolean isTouchTracking();

    /**
     * Stores that the given blob keys have been accessed.
     * <p>
     * This is used by {@link TouchWritebackLoop} to actually update the blobs. This method should not be
     * invoked externally.
     *
     * @param blobKeys the set of keys to mark as accessed
     */
    void markTouched(Set<String> blobKeys);

    /**
     * Returns how many days files are kept in the storage space before being deleted.
     *
     * @return the amount of days, 0 when indefinite
     */
    int getRetentionDays();

    /**
     * Defines for up to how many days a generated {@linkplain URLBuilder url} is valid.
     *
     * @return the amount of days
     */
    int getUrlValidityDays();

    /**
     * Returns a hasher for the configured checksum algorithm.
     *
     * @return the hasher or <tt>null</tt> if no valid checksum algorithm is configured
     */
    @Nullable
    Hasher getHasher();
}
