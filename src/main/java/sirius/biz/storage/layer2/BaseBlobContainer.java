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
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;

/**
 * Base class for {@link BlobContainer} and {@link BlobReferenceContainer}.
 */
abstract class BaseBlobContainer extends Composite {

    @Transient
    protected final String spaceName;

    @Transient
    protected final BaseEntity<?> owner;

    @Transient
    protected BlobStorageSpace space;

    @Part
    @Nullable
    protected static BlobStorage objectStorage;

    /**
     * Creates a new container for the given entity.
     *
     * @param owner     the entity to which the blobs are attached
     * @param spaceName the space used to store the attached files
     */
    protected BaseBlobContainer(BaseEntity<?> owner, String spaceName) {
        this.owner = owner;
        this.spaceName = spaceName;
    }

    /**
     * Returns the space which is in charge of managing the attached blobs.
     *
     * @return the space which stores the attached blobs
     */
    protected BlobStorageSpace getSpace() {
        if (space == null) {
            if (objectStorage == null) {
                throw Exceptions.handle().to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 2: No metadata storage framework has been enabled.",
                                        owner.getClass().getName())
                                .handle();
            }

            space = objectStorage.getSpace(spaceName);
        }

        return space;
    }
}
