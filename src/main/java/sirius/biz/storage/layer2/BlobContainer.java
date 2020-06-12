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
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Can be placed into a {@link BaseEntity} to attach various {@link Blob blobs} to it.
 * <p>
 * This also contains an automatic delete handler which removes all blobs once the referencing entity is deleted.
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
