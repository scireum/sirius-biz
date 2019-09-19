/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.KeyGenerator;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.web.http.Response;

import java.util.Optional;

/**
 * Represents a layer 2 storage space which manages {@link Blob blobs} and {@link Directory directories}.
 */
public abstract class BasicBlobStorageSpace implements BlobStorageSpace {

    @Part
    protected static ObjectStorage objectStorage;

    @Part
    protected static KeyGenerator keyGenerator;

    @Part
    protected static StorageUtils utils;

    protected String spaceName;
    protected boolean browsable;
    protected boolean readonly;
    private ObjectStorageSpace objectStorageSpace;

    protected BasicBlobStorageSpace(String spaceName, boolean browsable, boolean readonly) {
        this.spaceName = spaceName;
        this.browsable = browsable;
        this.readonly = readonly;
    }

    /**
     * Returns the associated layer 1 space which actually stores the data.
     *
     * @return the associated physical storage space
     */
    public ObjectStorageSpace getPhysicalSpace() {
        if (objectStorageSpace == null) {
            objectStorageSpace = objectStorage.getSpace(spaceName);
        }
        return objectStorageSpace;
    }

    @Override
    public boolean isBrowsable() {
        return browsable;
    }

    @Override
    public boolean isReadonly() {
        return readonly;
    }

    public void deliver(Blob blob, Response response) {
        if (Strings.isEmpty(blob.getPhysicalObjectId())) {
            response.error(HttpResponseStatus.NOT_FOUND);
            return;
        }

        getPhysicalSpace().deliver(response, blob.getPhysicalObjectId(), Files.getFileExtension(blob.getFilename()));
    }

    public Optional<FileHandle> download(Blob blob) {
        if (Strings.isEmpty(blob.getPhysicalObjectId())) {
            return Optional.empty();
        }

        return getPhysicalSpace().download(blob.getPhysicalObjectId());
    }
}
