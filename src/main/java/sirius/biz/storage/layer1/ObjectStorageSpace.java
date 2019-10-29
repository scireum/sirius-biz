/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.layer1.replication.ReplicationManager;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.web.http.Response;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Provides access to a layer 1 storage space.
 * <p>
 * This is essentially a bridge to the underlying {@link StorageEngine} for this space. This is also
 * responsible for invoking the {@link ReplicationManager} if needed.
 */
public class ObjectStorageSpace {

    private String name;
    private StorageEngine engine;

    @Part
    private static ReplicationManager replicationManager;

    protected ObjectStorageSpace(String name, StorageEngine engine) {
        this.name = name;
        this.engine = engine;
    }

    /**
     * Stores the given data for the given object key.
     *
     * @param objectId the physical storage key (a key is always only used once)
     * @param file     the data to store
     */
    public void upload(String objectId, File file) {
        try {
            engine.storePhysicalObject(name, objectId, file);
            replicationManager.notifyAboutUpdate(name, objectId);
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 1: An error occurred when uploading %s to %s (%s): %s (%s)",
                                                    file.getAbsolutePath(),
                                                    objectId,
                                                    name)
                            .handle();
        }
    }

    /**
     * Stores the given data for the given object key.
     *
     * @param objectId      the physical storage key (a key is always only used once)
     * @param inputStream   the data to store
     * @param contentLength the byte length of the data
     */
    public void upload(String objectId, InputStream inputStream, long contentLength) {
        try {
            engine.storePhysicalObject(name, objectId, inputStream, contentLength);
            replicationManager.notifyAboutUpdate(name, objectId);
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 1: An error occurred when uploading data to %s (%s): %s (%s)",
                                                    objectId,
                                                    name)
                            .handle();
        }
    }

    /**
     * Downloads and provides the contents of the requested object.
     *
     * @param objectId the physical storage key (a key is always only used once)
     * @return a handle to the given object wrapped as optional or an empty one if the object doesn't exist
     */
    public Optional<FileHandle> download(String objectId) {
        try {
            return Optional.ofNullable(engine.getData(name, objectId));
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 1: An error occurred when downloading %s (%s): %s (%s)",
                                                    objectId,
                                                    name)
                            .handle();
        }
    }

    /**
     * Delivers the requested object to the given HTTP response.
     * <p>
     * If replication is active and delivery from the primary storage fails a delivery from the backup space is
     * attempted automatically (for 5XX HTTP errors).
     *
     * @param response the response to populate
     * @param objectId the id of the object to deliver
     */
    public void deliver(Response response, String objectId) {
        try {
            engine.deliver(response, name, objectId, status -> handleHttpError(response, objectId, status));
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 1: An error occurred when delivering %s (%s) for %s: %s (%s)",
                                                    objectId,
                                                    name,
                                                    response.getWebContext().getRequestedURI())
                            .handle();
        }
    }

    private void handleHttpError(Response response, String objectId, int status) {
        ObjectStorageSpace replicationSpace = replicationManager.getReplicationSpace(name).orElse(null);
        if (replicationSpace == null) {
            response.error(HttpResponseStatus.valueOf(status));
            //TODO bump stats + record event
            return;
        }

        try {
            replicationSpace.engine.deliver(response, name, objectId, nextStatus -> {
                response.error(HttpResponseStatus.valueOf(nextStatus));
            });
        } catch (IOException e) {
            try {
                response.error(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                Exceptions.ignore(ex);
            }

            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 1: An error occurred when delivering %s (%s) via replication %s for %s: %s (%s)",
                                    objectId,
                                    name,
                                    replicationSpace.name,
                                    response.getWebContext().getRequestedURI())
                            .handle();
        }

        //TODO bump stats + record event
    }

    /**
     * Deletes the physical object in the given bucket with the given id
     *
     * @param objectId the id of the object to delete
     */
    public void delete(String objectId) {
        try {
            engine.deletePhysicalObject(name, objectId);
            replicationManager.notifyAboutDelete(name, objectId);
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 1: An error occurred when deleting %s (%s): %s (%s)",
                                                    objectId,
                                                    name)
                            .handle();
        }
    }
}
