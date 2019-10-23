/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.web.http.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

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
     *
     * @param tenantId the tenant to determine the directory for
     * @return the root directory for the given tenant
     * @throws sirius.kernel.health.HandledException if the space isn't {@link BlobStorage#CONFIG_KEY_LAYER2_BROWSABLE}
     */
    Directory getRoot(String tenantId);

    /**
     * Creates a new temporary blob to be used in a {@link BlobHardRef}.
     * <p>
     * Such objects will be automatically deleted if the referencing entity is
     * deleted. It is made permanent as soon as the referencing entity is saved, otherwise it will be deleted
     * automatically.
     *
     * @return the newly created temporary blob. To make this blob permanent, it has to be stored in a
     * {@link BlobHardRef} and the referencing entity has to be persisted.
     */
    Blob createTemporaryBlob();

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
     * Resolves the filename of the given blob.
     *
     * @param blobKey the blob to lookup the filename for
     * @return the filename if present or an empty optional if non was found
     */
    Optional<String> resolveFilename(@Nonnull String blobKey);

    /**
     * Delivers the requested blob to the given HTTP response.
     *
     * @param response the response to populate
     * @param blobKey  the {@link Blob#getBlobKey()} of the {@link Blob} to deliver
     * @param variant  the variant to deliver. Use {@link URLBuilder#VARIANT_RAW} to deliver the blob itself
     */
    void deliver(@Nonnull String blobKey, @Nonnull String variant, @Nonnull Response response);

    /**
     * Performs some housekeeping and maintenance tasks.
     * <p>
     * This shouldn't be invoked manually as it is triggered via the {@link StorageCleanupTask}.
     */
    void runCleanup();
}
