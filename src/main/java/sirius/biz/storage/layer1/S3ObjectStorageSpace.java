/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import com.amazonaws.services.s3.model.S3ObjectInputStream;
import sirius.biz.storage.layer1.transformer.ByteBlockTransformer;
import sirius.biz.storage.layer1.transformer.TransformingInputStream;
import sirius.biz.storage.s3.BucketName;
import sirius.biz.storage.s3.ObjectStore;
import sirius.biz.storage.s3.ObjectStores;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Streams;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Provides a {@link ObjectStorageSpace} which stores all objects in a bucket in a S3 compatible store.
 */
public class S3ObjectStorageSpace extends ObjectStorageSpace {

    /**
     * Contains the name of the {@link ObjectStore} used to connect to the S3 compatible store.
     */
    public static final String CONFIG_KEY_LAYER1_STORE = "store";

    @Part
    private static ObjectStores objectStores;

    private ObjectStore store;

    /**
     * Creates a new instance based on the given config.
     *
     * @param name      the name of the space to create
     * @param extension the configuration to create the space for
     */
    protected S3ObjectStorageSpace(String name, Extension extension) throws Exception {
        super(name, extension);
        this.store = resolveObjectStore(extension);
    }

    private ObjectStore resolveObjectStore(Extension extension) {
        String storeName = extension.getString(CONFIG_KEY_LAYER1_STORE);

        if (!objectStores.isConfigured(storeName)) {
            StorageUtils.LOG.WARN(
                    "Layer 1/S3: Unknown or unconfigured object store '%s' used by space '%s'. Using system default.",
                    storeName,
                    extension.getId());
            return objectStores.store();
        }

        return objectStores.getStore(storeName);
    }

    private BucketName bucketName() {
        return store.getBucketName(name);
    }

    @Override
    protected void storePhysicalObject(String objectKey, InputStream data, long size) throws IOException {
        if (size > 0) {
            store.upload(bucketName(), objectKey, data, size);
        } else {
            store.upload(bucketName(), objectKey, data);
        }
    }

    @Override
    protected void storePhysicalObject(String objectKey, InputStream data, ByteBlockTransformer transformer)
            throws IOException {
        try (InputStream effectiveStream = new TransformingInputStream(data, transformer)) {
            store.upload(bucketName(), objectKey, effectiveStream);
        }
    }

    @Override
    protected void storePhysicalObject(String objectKey, File file) throws IOException {
        store.upload(bucketName(), objectKey, file);
    }

    @Override
    protected void storePhysicalObject(String objectKey, File file, ByteBlockTransformer transformer)
            throws IOException {
        storePhysicalObject(objectKey, new FileInputStream(file), transformer);
    }

    @Override
    protected void deletePhysicalObject(String objectKey) throws IOException {
        store.deleteObject(bucketName(), objectKey);
    }

    @Override
    protected void deliverPhysicalObject(Response response, String objectKey, IntConsumer failureHandler)
            throws IOException {
        response.tunnel(store.objectUrl(bucketName(), objectKey), failureHandler);
    }

    @Override
    protected void deliverPhysicalObject(Response response,
                                         String objectKey,
                                         ByteBlockTransformer transformer,
                                         @Nullable IntConsumer failureHandler) throws IOException {
        response.tunnel(store.objectUrl(bucketName(), objectKey), buffer -> {
            if (buffer.isReadable()) {
                return transformer.apply(buffer);
            } else {
                return transformer.complete();
            }
        }, failureHandler);
    }

    @Nullable
    @Override
    protected FileHandle getData(String objectKey) throws IOException {
        try {
            return FileHandle.temporaryFileHandle(store.download(bucketName(), objectKey));
        } catch (FileNotFoundException e) {
            Exceptions.ignore(e);
            return null;
        }
    }

    @Nullable
    @Override
    protected FileHandle getData(String objectKey, ByteBlockTransformer transformer) throws IOException {
        File dest = null;
        try {
            dest = File.createTempFile("AMZS3", null);
            try (FileOutputStream out = new FileOutputStream(dest);
                 InputStream in = getAsStream(objectKey, transformer)) {
                Streams.transfer(in, out);
            }
            return FileHandle.temporaryFileHandle(dest);
        } catch (Exception e) {
            Files.delete(dest);
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 1/S3: An error occurred while trying to download: %s/%s - %s (%s)",
                                    name,
                                    objectKey)
                            .handle();
        }
    }

    @Nullable
    @Override
    protected InputStream getAsStream(String objectKey) throws IOException {
        return store.getClient().getObject(bucketName().getName(), objectKey).getObjectContent();
    }

    @Nullable
    @Override
    protected InputStream getAsStream(String objectKey, ByteBlockTransformer transformer) throws IOException {
        S3ObjectInputStream rawStream =
                store.getClient().getObject(bucketName().getName(), objectKey).getObjectContent();
        return new TransformingInputStream(rawStream, transformer);
    }

    @Override
    public void iterateObjects(Predicate<String> physicalKeyHandler) throws IOException {
        store.listObjects(bucketName(), null, s3Object -> {
            return physicalKeyHandler.test(s3Object.getKey());
        });
    }
}
