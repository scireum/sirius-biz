/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import io.netty.handler.codec.http.HttpHeaderNames;
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
import sirius.kernel.async.Promise;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Counter;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.ChunkedOutputStream;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import java.util.function.BiConsumer;
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
    @Nullable
    private static ReplicationManager replicationManager;

    @Part
    private static GlobalContext globalContext;

    @Part
    private static Tasks tasks;

    @Part
    protected static ObjectStorage objectStorage;

    private static final Counter UPLOADS = new Counter();
    private static final Counter DOWNLOADS = new Counter();
    private static final Counter STREAMS = new Counter();
    private static final Counter DELIVERIES = new Counter();
    private static final Counter FALLBACKS = new Counter();
    private static final Counter DELIVERY_CLIENT_FAILURES = new Counter();
    private static final Counter DELIVERY_SERVER_FAILURES = new Counter();

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
     * Determines if either a compression or an encryption (or both) transformer is present.
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
     * @return a cipher transformer with the appropriate encryption / cipher settings
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
     * @return an inflate transformer to use
     */
    protected ByteBlockTransformer createInflater() {
        return new InflateTransformer();
    }

    /**
     * Creates a new cipher transformer which is used to decrypt data being read.
     *
     * @return a cipher transformer with the appropriate encryption / cipher settings
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
            UPLOADS.inc();
            if (hasTransformer()) {
                storePhysicalObject(objectId, file, createWriteTransformer());
                replicationManager.notifyAboutUpdate(this, objectId, 0);
            } else {
                storePhysicalObject(objectId, file);
                replicationManager.notifyAboutUpdate(this, objectId, file.length());
            }
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
     * Copies the given object to the given target object key in the current storage space.
     *
     * @param sourceObjectKey    the source object key to copy from
     * @param targetObjectKey    the target object key to copy to
     * @param targetStorageSpace the target storage space to copy to
     */
    public void duplicatePhysicalObject(String sourceObjectKey, String targetObjectKey, String targetStorageSpace) {
        download(sourceObjectKey).ifPresent(fileHandle -> {
            try (fileHandle) {
                objectStorage.getSpace(targetStorageSpace).upload(targetObjectKey, fileHandle.getFile());
            }
        });
    }

    /**
     * Copies the given object to the given target object key in the current storage space.
     *
     * @param sourceObjectKey the source object key to copy from
     * @param targetObjectKey the target object key to copy to
     */
    public void duplicatePhysicalObject(String sourceObjectKey, String targetObjectKey) {
        duplicatePhysicalObject(sourceObjectKey, targetObjectKey, getName());
    }

    /**
     * Stores the given data for the given object key.
     *
     * @param objectId      the physical storage key (a key is always only used once)
     * @param inputStream   the data to store
     * @param contentLength the byte length of the data or 0 to indicate that the length is unknown
     */
    public void upload(String objectId, InputStream inputStream, long contentLength) {
        try {
            UPLOADS.inc();
            if (hasTransformer()) {
                storePhysicalObject(objectId, inputStream, createWriteTransformer());
                replicationManager.notifyAboutUpdate(this, objectId, 0);
            } else {
                storePhysicalObject(objectId, inputStream, contentLength);
                replicationManager.notifyAboutUpdate(this, objectId, contentLength);
            }
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
     * @param size      the byte length of the data or 0 to indicate that the length is unknown
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
     * <p>
     * Note that the returned {@link FileHandle} must be closed once the data has been processed to ensure proper cleanup.
     * Do this ideally with a {@code try-with-resources} block:
     * <pre>
     * space.download(objectId).ifPresent(handle -> {
     *     try (handle) {
     *         // Read from the handle here...
     *     }
     * });
     * </pre>
     *
     * @param objectId the physical storage key
     * @return a {@linkplain java.io.Closeable closeable} file handle to the given object wrapped as optional, or an empty one if the object doesn't exist
     */
    public Optional<FileHandle> download(String objectId) {
        try {
            if (Strings.isEmpty(objectId)) {
                return Optional.empty();
            }
            DOWNLOADS.inc();
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
     * Tries to perform the download asynchronous and yields an appropriate promise.
     *
     * @param objectId the object to download
     * @return a promise which is fulfilled with the downloaded data. Note that an empty optional is used to signal
     * that the object with the given ID doesn't exist
     */
    public Promise<Optional<FileHandle>> downloadAsync(String objectId) {
        Promise<Optional<FileHandle>> promise = new Promise<>();
        if (Strings.isEmpty(objectId)) {
            promise.success(Optional.empty());
        } else if (hasTransformer()) {
            getDataAsync(objectId, createReadTransformer()).mapChain(promise, Optional::ofNullable);
        } else {
            getDataAsync(objectId).mapChain(promise, Optional::ofNullable);
        }

        return promise;
    }

    /**
     * Creates a new {@link DownloadManager} for this storage space.
     *
     * @param completionConsumer the handler which is supplied with all downloaded objects
     * @param failureHandler     the handler which is  invoked if an error occurs
     * @param <P>                the payload type which is carried along so that the callbacks have some context
     * @return a new download manager for this space
     */
    public <P> DownloadManager<P> downloadManager(BiConsumer<P, Optional<FileHandle>> completionConsumer,
                                                  BiConsumer<P, Exception> failureHandler) {
        return new DownloadManager<>(this, completionConsumer, (payload, innerError) -> {
            failureHandler.accept(payload,
                                  Exceptions.handle()
                                            .error(innerError)
                                            .to(StorageUtils.LOG)
                                            .withSystemErrorMessage(
                                                    "Layer 1: Failed to perform a download using the DownloadManager: %s (%s)")
                                            .handle());
        });
    }

    /**
     * Downloads and provides the contents of the requested object.
     *
     * @param objectKey the id of the object
     * @return a handle to the given object or <tt>null</tt> if the object doesn't exist
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract FileHandle getData(String objectKey) throws IOException;

    /**
     * Downloads and provides the contents of the requested object asynchronous.
     *
     * @param objectKey the id of the object
     * @return a promise which is fulfilled with the handle for the given object once it is downloaded. If the object
     * doesn't exist, the promise will be fulfilled with <tt>null</tt>.
     */
    protected abstract Promise<FileHandle> getDataAsync(String objectKey);

    /**
     * Downloads and provides the contents of the requested object.
     *
     * @param objectKey   the id of the object
     * @param transformer the transform to apply when reading data
     * @return a handle to the given object or <tt>null</tt> if the object doesn't exist
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract FileHandle getData(String objectKey, ByteBlockTransformer transformer) throws IOException;

    /**
     * Downloads and provides the contents of the requested object asynchronous.
     *
     * @param objectKey   the id of the object
     * @param transformer the transform to apply when reading data
     * @return a promise which is fulfilled with the handle for the given object once it is downloaded. If the object
     * doesn't exist, the promise will be fulfilled with <tt>null</tt>.
     */
    protected abstract Promise<FileHandle> getDataAsync(String objectKey, ByteBlockTransformer transformer);

    /**
     * Provides direct access to the contents of the requested object.
     *
     * @param objectId the physical storage key
     * @return the contents as input stream
     */
    public Optional<InputStream> getInputStream(String objectId) {
        try {
            if (Strings.isEmpty(objectId)) {
                return Optional.empty();
            }

            STREAMS.inc();
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
     * Provides the contents of the request object as input stream.
     *
     * @param objectKey the id of the object
     * @return an input stream which provides the contents of the object
     * @throws IOException in case of an IO error
     */
    @Nullable
    protected abstract InputStream getAsStream(String objectKey) throws IOException;

    /**
     * Provides the contents of the request object as input stream.
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
     * @param response          the response to populate
     * @param objectId          the id of the object to deliver
     * @param largeFileExpected determines if a large file is expected
     */
    public void deliver(Response response, String objectId, boolean largeFileExpected) {
        try {
            DELIVERIES.inc();

            if (hasTransformer()) {
                deliverPhysicalObject(response,
                                      objectId,
                                      createReadTransformer(),
                                      status -> handleHttpError(response, objectId, status, largeFileExpected));
            } else if (shouldHandleAsLargeFile(response, largeFileExpected)) {
                deliverLarge(response, objectId);
            } else {
                deliverPhysicalObject(response, objectId, status -> handleHttpError(response, objectId, status, false));
            }
        } catch (Exception exception) {
            handleDeliveryError(response, objectId, exception);
        }
    }

    /**
     * Determines if a special delivery for very large files should be attempted.
     * <p>
     * For very large files (multiple GBs), we use special URLs generated by the
     * {@link sirius.biz.storage.layer2.URLBuilder} and handled by the {@link sirius.biz.storage.layer2.BlobDispatcher}.
     * These contain the "/xxl/" marker so that downstream caches like <tt>Varnish</tt> don't even attempt to put the
     * data in their cache. These files therefore are piped through which may lead to increasingly large internal
     * buffers as an upstream storage like <b>S3</b> might send data faster than we pump through to slower clients
     * like mobile users. We therefore use a blocking/stream based approach in a thread pool of limited size. However,
     * in the presence of a <tt>Range</tt> header, the data to transfer is most probably way smaller and also the stream
     * implementation cannot satisfy such requests. We therefore only use this approach if a large file is expected and
     * no <tt>Range</tt> header is present.
     *
     * @param response          the response to handle
     * @param largeFileExpected a flag which determines if a large file is expected at all
     * @return <tt>true</tt> if the delivery should be handled in a separate thread pool and a non-blocking fashion
     */
    private boolean shouldHandleAsLargeFile(Response response, boolean largeFileExpected) {
        return largeFileExpected && !response.getWebContext().getHeaderValue(HttpHeaderNames.RANGE).isFilled();
    }

    private void handleDeliveryError(Response response, String objectId, Exception exception) {
        if (exception instanceof ClosedChannelException || exception.getCause() instanceof ClosedChannelException) {
            // If the user unexpectedly closes the connection, we do not need to log an error...
            Exceptions.ignore(exception);
            return;
        }
        try {
            response.error(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            Exceptions.ignore(ex);
        }
        throw Exceptions.handle()
                        .error(exception)
                        .to(StorageUtils.LOG)
                        .withSystemErrorMessage("Layer 1: An error occurred when delivering %s (%s) for %s: %s (%s)",
                                                objectId,
                                                name,
                                                response.getWebContext().getRequestedURI())
                        .handle();
    }

    private void deliverLarge(Response response, String objectId) {
        response.getWebContext().markAsLongCall();

        tasks.executor("storage-deliver-large-file")
             .dropOnOverload(() -> response.error(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                                  "Large file delivery system overloaded!"))
             .fork(() -> {
                 getInputStream(objectId).ifPresentOrElse(input -> {
                     try (InputStream in = input;
                          ChunkedOutputStream out = response.outputStream(HttpResponseStatus.OK, null)) {
                         out.enableContentionControl();
                         Streams.transfer(in, out);
                     } catch (Exception exception) {
                         handleDeliveryError(response, objectId, exception);
                     }
                 }, () -> {
                     handleHttpError(response, objectId, HttpResponseStatus.NOT_FOUND.code(), true);
                 });
             });
    }

    private void handleHttpError(Response response, String objectId, int status, boolean largeFileExpected) {
        if (replicationSpace != null) {
            FALLBACKS.inc();
            replicationSpace.deliver(response, objectId, largeFileExpected);
        } else {
            if (status >= 500) {
                DELIVERY_SERVER_FAILURES.inc();
            } else if (status >= 400) {
                DELIVERY_CLIENT_FAILURES.inc();
            }

            response.error(HttpResponseStatus.valueOf(status));
        }
    }

    /**
     * Delivers the requested object to the given HTTP response.
     *
     * @param response       the response to populate
     * @param objectKey      the id of the object to deliver
     * @param failureHandler a handler which can be invoked if the download cannot be performed.
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
     * @param objectHandler a handler to be invoked for every object found in this space. Returns <tt>true</tt>
     *                      to continue iterating and <tt>false</tt> to abort.
     * @throws IOException in case of an IO error
     */
    public abstract void iterateObjects(Predicate<ObjectMetadata> objectHandler) throws IOException;

    /**
     * Returns the configuration block which was used to set up this space.
     *
     * @return the settings of this space
     */
    public Extension getSettings() {
        return this.settings;
    }

    /**
     * Specifies the replication space to use.
     * <p>
     * This should be exclusively used by  the {@link ReplicationManager} to set up the replication framework.
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

    /**
     * Counts the number of uploads performed on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of uploads on this node
     */
    public static long getUploads() {
        return UPLOADS.getCount();
    }

    /**
     * Counts the number of downloads performed on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of downloads on this node
     */
    public static long getDownloads() {
        return DOWNLOADS.getCount();
    }

    /**
     * Counts the number of stream fetches performed on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of stream fetches on this node
     */
    public static long getStreams() {
        return STREAMS.getCount();
    }

    /**
     * Counts the number of deliveries performed on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of deliveries on this node
     */
    public static long getDeliveries() {
        return DELIVERIES.getCount();
    }

    /**
     * Counts the number of deliveries which had to use the fallback repository on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of fallbacks on this node
     */
    public static long getFallbacks() {
        return FALLBACKS.getCount();
    }

    /**
     * Counts the number of client errors during deliveries on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of client errors on this node
     */
    public static long getDeliveryClientFailures() {
        return DELIVERY_CLIENT_FAILURES.getCount();
    }

    /**
     * Counts the number of server errors during deliveries on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the number of server errors on this node
     */
    public static long getDeliveryServerFailures() {
        return DELIVERY_SERVER_FAILURES.getCount();
    }
}
