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
import sirius.biz.storage.layer1.transformer.ByteBlockTransformer;
import sirius.biz.storage.layer1.transformer.CipherFactory;
import sirius.biz.storage.layer1.transformer.CipherProvider;
import sirius.biz.storage.layer1.transformer.CipherTransformer;
import sirius.biz.storage.layer1.transformer.CombinedTransformer;
import sirius.biz.storage.layer1.transformer.CompressionLevel;
import sirius.biz.storage.layer1.transformer.DeflateTransformer;
import sirius.biz.storage.layer1.transformer.InflateTransformer;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Provides access to a layer 1 storage space.
 */
public abstract class ObjectStorageSpace {

    protected final String name;
    protected Extension settings;

    private CompressionLevel compression;
    private boolean useCompression;

    private CipherProvider cipherProvider;
    private boolean useEncryption;

    private ObjectStorageSpace replicationSpace;

    @Part
    private static ReplicationManager replicationManager;

    @Part
    private static GlobalContext globalContext;

    /**
     * Creates a new instance with the given name and configuration.
     *
     * @param name      the name of the space
     * @param extension the configuration of the space
     * @throws Exception in case of an invalid config
     */
    protected ObjectStorageSpace(String name, Extension extension) throws Exception {
        this.name = name;
        this.settings = extension;

        setupCompression();
        setupEncryption();
    }

    /**
     * Reads and applies the compression settings.
     */
    private void setupCompression() {
        this.compression = Value.of(settings.get(ObjectStorage.CONFIG_KEY_LAYER1_COMPRESSION).toUpperCase())
                                .getEnum(CompressionLevel.class)
                                .orElse(CompressionLevel.OFF);
        this.useCompression = this.compression != CompressionLevel.OFF;
    }

    /**
     * Reads and applies the encryption settings.
     *
     * @throws Exception in case of a config error
     */
    private void setupEncryption() throws Exception {
        String cipherFactoryName = settings.get(ObjectStorage.CONFIG_KEY_LAYER1_CIPHER).asString();
        if (Strings.isFilled(cipherFactoryName)) {
            this.cipherProvider = globalContext.getPart(cipherFactoryName, CipherFactory.class).create(settings);
            this.useEncryption = true;
        }
    }

    /**
     * Determines if either a compression or an encrpytion (or both) transformer is present.
     *
     * @return <tt>true</tt> if there is at least one transformer present, <tt>false</tt> otherwise
     */
    protected boolean hasTransformer() {
        return useEncryption || useCompression;
    }

    /**
     * Creates the write transformer to apply.
     *
     * @return the write transformer based on the system configuration
     */
    protected ByteBlockTransformer createWriteTransformer() {
        if (useCompression) {
            if (useEncryption) {
                return new CombinedTransformer(createDeflater(), createEncrypter());
            } else {
                return createDeflater();
            }
        } else if (useEncryption) {
            return createEncrypter();
        } else {
            throw new IllegalStateException("No transformer to create");
        }
    }

    /**
     * Creates a new deflate transformer used when writing data.
     *
     * @return a deflate transformer with the appropriate compression setting
     */
    protected ByteBlockTransformer createDeflater() {
        return new DeflateTransformer(compression);
    }

    /**
     * Creates a new cipher transformer which is used to encrypt data being written.
     *
     * @return a cipher transfomer with the appropriate encryption / cipher settings
     */
    protected ByteBlockTransformer createEncrypter() {
        return new CipherTransformer(cipherProvider.createEncryptionCipher());
    }

    /**
     * Creates the read transformer to apply.
     *
     * @return the read transformer based on the system configuration
     */
    protected ByteBlockTransformer createReadTransformer() {
        if (useCompression) {
            if (useEncryption) {
                return new CombinedTransformer(createDecrypter(), createInflater());
            } else {
                return createInflater();
            }
        } else if (useEncryption) {
            return createDecrypter();
        } else {
            throw new IllegalStateException("No transformer to create");
        }
    }

    /**
     * Creates a new inflate transformer used when reading data.
     *
     * @return a inflate transformer to use
     */
    protected ByteBlockTransformer createInflater() {
        return new InflateTransformer();
    }

    /**
     * Creates a new cipher transformer which is used to decrypt data being read.
     *
     * @return a cipher transfomer with the appropriate encryption / cipher settings
     */
    protected ByteBlockTransformer createDecrypter() {
        return new CipherTransformer(cipherProvider.createDecryptionChiper());
    }

    /**
     * Stores the given data for the given object key.
     *
     * @param objectId the physical storage key (a key is always only used once)
     * @param file     the data to store
     */
    public void upload(String objectId, File file) {
        try {
            if (hasTransformer()) {
                storePhysicalObject(objectId, file, createWriteTransformer());
            } else {
                storePhysicalObject(objectId, file);
            }
            replicationManager.notifyAboutUpdate(this, objectId);
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
     * Stores the given data for the given key in the given bucket.
     *
     * @param objectKey the physical storage key (a key is always only used once)
     * @param file      the data to store
     * @throws IOException in case of an IO error
     */
    protected abstract void storePhysicalObject(String objectKey, File file) throws IOException;

    /**
     * Stores the given data for the given key in the given bucket.
     *
     * @param objectKey   the physical storage key (a key is always only used once)
     * @param file        the data to store
     * @param transformer the transformer to apply when storing data
     * @throws IOException in case of an IO error
     */
    protected abstract void storePhysicalObject(String objectKey, File file, ByteBlockTransformer transformer)
            throws IOException;

    /**
     * Stores the given data for the given object key.
     *
     * @param objectId      the physical storage key (a key is always only used once)
     * @param inputStream   the data to store
     * @param contentLength the byte length of the data
     */
    public void upload(String objectId, InputStream inputStream, long contentLength) {
        try {
            if (hasTransformer()) {
                storePhysicalObject(objectId, inputStream, createWriteTransformer());
            } else {
                storePhysicalObject(objectId, inputStream, contentLength);
            }
            replicationManager.notifyAboutUpdate(this, objectId);
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
     * Stores the given data for the given key in the given bucket.
     *
     * @param objectKey the physical storage key (a key is always only used once)
     * @param data      the data to store
     * @param size      the byte length of the data
     * @throws IOException in case of an IO error
     */
    protected abstract void storePhysicalObject(String objectKey, InputStream data, long size) throws IOException;

    /**
     * Stores the given data for the given key in the given bucket.
     *
     * @param objectKey   the physical storage key (a key is always only used once)
     * @param data        the data to store
     * @param transformer the transformer to apply when storing data
     * @throws IOException in case of an IO error
     */
    protected abstract void storePhysicalObject(String objectKey, InputStream data, ByteBlockTransformer transformer)
            throws IOException;

    /**
     * Downloads and provides the contents of the requested object.
     *
     * @param objectId the physical storage key
     * @return a handle to the given object wrapped as optional or an empty one if the object doesn't exist
     */
    public Optional<FileHandle> download(String objectId) {
        try {
            if (Strings.isEmpty(objectId)) {
                return Optional.empty();
            }
            if (hasTransformer()) {
                return Optional.ofNullable(getData(objectId, createReadTransformer()));
            } else {
                return Optional.ofNullable(getData(objectId));
            }
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
     * Downloads an provides the contents of the requested object.
     *
     * @param objectKey the id of the object
     * @return a handle to the given object or <tt>null</tt> if the object doesn't exist
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract FileHandle getData(String objectKey) throws IOException;

    /**
     * Downloads an provides the contents of the requested object.
     *
     * @param objectKey   the id of the object
     * @param transformer the transform to apply when reading data
     * @return a handle to the given object or <tt>null</tt> if the object doesn't exist
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract FileHandle getData(String objectKey, ByteBlockTransformer transformer) throws IOException;

    /**
     * Provides direct access to the contents of the requested object.
     *
     * @param objectId the physical storage key
     * @return the contents a input stream
     */
    public Optional<InputStream> getInputStream(String objectId) {
        try {
            if (Strings.isEmpty(objectId)) {
                return Optional.empty();
            }
            if (hasTransformer()) {
                return Optional.ofNullable(getAsStream(objectId, createReadTransformer()));
            } else {
                return Optional.ofNullable(getAsStream(objectId));
            }
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 1: An error occurred when obtaining an input stream for %s (%s): %s (%s)",
                                    objectId,
                                    name)
                            .handle();
        }
    }

    /**
     * Provides the contents of the requrest object as input stream.
     *
     * @param objectKey the id of the object
     * @return an input stream which provides the contents of the object
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract InputStream getAsStream(String objectKey) throws IOException;

    /**
     * Provides the contents of the requrest object as input stream.
     *
     * @param objectKey the id of the object
     * @return an input stream which provides the contents of the object
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract InputStream getAsStream(String objectKey, ByteBlockTransformer transformer) throws IOException;

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
            if (hasTransformer()) {
                deliverPhysicalObject(response,
                                      objectId,
                                      createReadTransformer(),
                                      status -> handleHttpError(response, objectId, status));
            } else {
                deliverPhysicalObject(response, objectId, status -> handleHttpError(response, objectId, status));
            }
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
        if (replicationSpace != null) {
            //TODO bump stats + record event
            replicationSpace.deliver(response, objectId);
        } else {
            //TODO bump stats + record event
            response.error(HttpResponseStatus.valueOf(status));
        }
    }

    /**
     * Delivers the requested object to the given HTTP response.
     *
     * @param response       the response to populate
     * @param objectKey      the id of the object to deliver
     * @param failureHandler a handler which cann be invoked if the download cannot be performed.
     *                       This will be supplied with the HTTP error code.
     * @throws IOException in case of an IO error
     */
    protected abstract void deliverPhysicalObject(Response response,
                                                  String objectKey,
                                                  @Nullable IntConsumer failureHandler) throws IOException;

    /**
     * Delivers the requested object to the given HTTP response.
     *
     * @param response       the response to populate
     * @param objectKey      the id of the object to deliver
     * @param transformer    the transform to apply when delivering data
     * @param failureHandler a handler which can be invoked if the download cannot be performed.
     *                       This will be supplied with the HTTP error code.
     * @throws IOException in case of an IO error
     */
    protected abstract void deliverPhysicalObject(Response response,
                                                  String objectKey,
                                                  ByteBlockTransformer transformer,
                                                  @Nullable IntConsumer failureHandler) throws IOException;

    /**
     * Deletes the physical object in the given bucket with the given id
     *
     * @param objectId the id of the object to delete
     */
    public void delete(String objectId) {
        try {
            deletePhysicalObject(objectId);
            replicationManager.notifyAboutDelete(this, objectId);
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

    /**
     * Deletes the physical object in the given bucket with the given id
     *
     * @param objectKey the id of the object to delete
     * @throws IOException in case of an IO error
     */
    protected abstract void deletePhysicalObject(String objectKey) throws IOException;

    /**
     * Returns the name of this storage space.
     *
     * @return the name of this space
     */
    public String getName() {
        return name;
    }

    /**
     * Iterates over all known objects in this space.
     * <p>
     * Note that this is most probably a very inefficient operation and should only be used for maintenance or
     * debugging tasks.
     *
     * @param physicalKeyHandler a handler to be invoked for every key found in this space. Returns <tt>true</tt>
     *                           to continue iterating and <tt>false</tt> to abort.
     * @throws IOException in case of an IO error
     */
    public abstract void iterateObjects(Predicate<String> physicalKeyHandler) throws IOException;

    /**
     * Returns the configuration block which was used to setup this space.
     *
     * @return the settings of this space
     */
    public Extension getSettings() {
        return this.settings;
    }

    /**
     * Specifies the replication space to use.
     * <p>
     * This should be exlusively used by  the {@link ReplicationManager} to setup the replication framework.
     *
     * @param replicationSpace the space to replicate all data to
     */
    public void withReplicationSpace(ObjectStorageSpace replicationSpace) {
        this.replicationSpace = replicationSpace;
    }

    /**
     * Returns the replication space assigned to this space.
     *
     * @return the replication space to which all data is replicated to
     */
    @Nullable
    public ObjectStorageSpace getReplicationSpace() {
        return this.replicationSpace;
    }

    /**
     * Determines if a replication space is available.
     *
     * @return <tt>true</tt> if a replication space is available, <tt>false</tt> otherwise
     */
    public boolean hasReplicationSpace() {
        return replicationSpace != null;
    }
}
