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
import sirius.biz.storage.util.StorageUtils;
import sirius.db.KeyGenerator;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;

/**
 * Represents a base implementation for a layer 2 storage space which manages {@link Blob blobs} and
 * {@link Directory directories}.
 */
public abstract class BasicBlobStorageSpace<B extends Blob, D extends Directory> implements BlobStorageSpace {

    @Part
    protected static ObjectStorage objectStorage;

    @Part
    protected static KeyGenerator keyGenerator;

    @Part
    protected static StorageUtils utils;

    @Part
    private static Locks locks;

    protected static Cache<String, Directory> directoryByIdCache =
            CacheManager.createCoherentCache("storage-directories");

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

    @Override
    public Directory getRoot(String tenantId) {
        // Attempt a fast lookup without any locking as most probably the directory will exist...
        D directory = findRoot(tenantId);

        if (directory == null) {
            if (locks.tryLock("create-root-" + tenantId, Duration.ofSeconds(2))) {
                try {
                    // We need to perform the lookup again while holding the lock - who knows
                    // what other nodes did inbetween..
                    directory = findRoot(tenantId);
                    if (directory == null) {
                        directory = createRoot(tenantId);
                    }
                } finally {
                    locks.unlock("create-root-" + tenantId);
                }
            }
        }

        return fetchDirectoryById(directory.getIdAsString());
    }

    /**
     * Tries to find the root directory for the given tenant.
     *
     * @param tenantId the id of the tenant to find the root directory for
     * @return the root directory or <tt>null</tt> if it doesn't exist yet
     */
    @Nullable
    protected abstract D findRoot(String tenantId);

    /**
     * Creates the root directory for the given tenant.
     *
     * @param tenantId the id of the tenant to create the root directory for
     * @return the root directory for the given tenant
     */
    protected abstract D createRoot(String tenantId);

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
     * Delivers the blob into the given response.
     *
     * @param blob     the blob to deliver
     * @param response the response to fullfill
     */
    public void deliver(Blob blob, Response response) {
        if (Strings.isEmpty(blob.getPhysicalObjectId())) {
            response.error(HttpResponseStatus.NOT_FOUND);
            return;
        }

        getPhysicalSpace().deliver(response, blob.getPhysicalObjectId(), Files.getFileExtension(blob.getFilename()));
    }

    /**
     * Performs a download / fetch of the given blob to make its data locally accessible.
     *
     * @param blob the blob to fetch the data for
     * @return a file handle which makes the blob data accessible or an empty optional if no data was present.
     * Note that the {@link FileHandle} must be closed once the data has been processed to ensure proper cleanup.
     */
    public Optional<FileHandle> download(Blob blob) {
        if (Strings.isEmpty(blob.getPhysicalObjectId())) {
            return Optional.empty();
        }

        return getPhysicalSpace().download(blob.getPhysicalObjectId());
    }
}
