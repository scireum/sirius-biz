/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.util.StorageUtils;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.kernel.health.Exceptions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Can be placed into a {@link BaseEntity} to attach various {@link Blob blobs} to it.
 * <p>
 * This also contains an automatic delete handler which removes all blobs once the referencing entity is deleted.
 *
 * @see BlobReferenceContainer
 */
public class BlobContainer extends BaseBlobContainer {

    /**
     * Creates a new container for the given entity.
     *
     * @param owner     the entity to which the blobs are attached
     * @param spaceName the space used to store the attached files
     */
    public BlobContainer(BaseEntity<?> owner, String spaceName) {
        super(owner, spaceName);
    }

    /**
     * Tries to resolve the blob with the given filename which has been attached to the referencing entity.
     * <p>
     * Note that this is mainly intended to be used in conjunction with {@link #findOrCreateAttachedBlobByName(String)}.
     * If multiple blobs with the same name are attached using {@link #attachTemporaryBlob(String)}, this might
     * match one of the blob, with no guarantees which of the files is matched.
     *
     * @param filename the file to lookup
     * @return the blob with the given name wrapped as optional or an empty optional if no matching blob was found
     */
    public Optional<? extends Blob> findAttachedBlobByName(String filename) {
        if (owner.isNew() || objectStorage == null) {
            return Optional.empty();
        }
        return getSpace().findAttachedBlobByName(owner.getUniqueName(), filename);
    }

    /**
     * Tries to resolve the blob with the given filename which has been attached to the referencing entity or creates
     * a new one if none is found.
     *
     * @param filename the file to lookup
     * @return the blob with the given name which is attached to the referencing entity
     * @throws sirius.kernel.health.HandledException if the referencing entity is new and hasn't been persisted yet
     */
    public Blob findOrCreateAttachedBlobByName(String filename) {
        if (owner.isNew()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2: Cannot create an attached object for a non-persistent entity of type %s",
                                    owner.getClass().getName())
                            .handle();
        }
        return getSpace().findOrCreateAttachedBlobByName(owner.getUniqueName(), filename);
    }

    /**
     * Resolves a blob which has been attached to the owning entity.
     *
     * @param blobKey the blob key to lookup
     * @return the matching blob or an empty optional if none is found
     */
    public Optional<? extends Blob> findAttachedBlobByKey(String blobKey) {
        if (owner.isNew() || objectStorage == null) {
            return Optional.empty();
        }

        return getSpace().findAttachedBlobByKey(owner.getUniqueName(), blobKey);
    }

    /**
     * Attaches a temporary blob to this container.
     *
     * @param temporaryBlob the temporary blob which has been created using
     *                      {@link BlobStorageSpace#createTemporaryBlob()}
     */
    public void attachTemporaryBlob(Blob temporaryBlob) {
        attachTemporaryBlob(temporaryBlob.getBlobKey());
    }

    /**
     * Attaches a temporary blob to this container.
     *
     * @param blobKey the blob key of the temporary blob to attach
     */
    public void attachTemporaryBlob(String blobKey) {
        if (owner.isNew()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2: Cannot attach an object for a non-persistent entity of type %s",
                                    owner.getClass().getName())
                            .handle();
        }

        getSpace().attachTemporaryBlob(blobKey, owner.getUniqueName());
    }

    /**
     * Lists all attached blobs.
     *
     * @return the list of all attached blobs
     */
    public List<? extends Blob> findAttachedBlobs() {
        if (objectStorage == null) {
            return Collections.emptyList();
        }
        return getSpace().findAttachedBlobs(owner.getUniqueName());
    }

    @AfterDelete
    protected void onDelete() {
        if (!owner.isNew() && objectStorage != null) {
            getSpace().deleteAttachedBlobs(owner.getUniqueName());
        }
    }
}
