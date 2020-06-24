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
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Can be placed into a {@link BaseEntity} to attach various {@link Blob blobs} to it.
 * <p>
 * Compared to {@link BlobContainer}, this only references <b>existing</b> blobs which are e.g. created via
 * {@link BlobStorageSpace#findOrCreateByPath(String)}
 *
 * @see BlobContainer
 */
public class BlobReferenceContainer extends BaseBlobContainer {

    /**
     * Creates a new container for the given entity.
     *
     * @param owner     the entity to which the blobs are attached
     * @param spaceName the space used to store the attached files
     */
    public BlobReferenceContainer(BaseEntity<?> owner, String spaceName) {
        super(owner, spaceName);
    }

    /**
     * Tries to resolve the blob which has been attached for the given type to the referencing entity.
     *
     * @param type the type for which the blob was attached earlier
     * @return the blob with the given type wrapped as optional or an empty optional if no matching blob was found
     */
    public Optional<? extends Blob> findReferencedBlobByType(String type) {
        if (owner.isNew() || objectStorage == null) {
            return Optional.empty();
        }
        return getSpace().findAttachedBlobByType(owner.getUniqueName(), type);
    }

    /**
     * Attaches the given blob with the given type to the referenced entity.
     * <p>
     * Note that the type can be any custom value, but must not match the name of a {@link BlobHardRef} or
     * {@link BlobSoftRef} in the entity.
     *
     * @param blob the blob to attach
     * @param type the type to attach the blob for
     */
    public void attachBlob(Blob blob, @Nonnull String type) {
        if (owner.isNew()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2: Cannot create an attached object for a non-persistent entity of type %s",
                                    owner.getClass().getName())
                            .handle();
        }
        if (blob == null) {
            return;
        }
        if (Strings.isEmpty(type)) {
            throw new IllegalArgumentException("type most not be empty!");
        }

        getSpace().attachBlobByType(blob.getBlobKey(), owner.getUniqueName(), type);
    }

}
