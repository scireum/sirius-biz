/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import com.amazonaws.services.s3.transfer.Upload;
import sirius.biz.storage.s3.BucketName;
import sirius.biz.storage.s3.ObjectStore;
import sirius.biz.storage.s3.ObjectStores;
import sirius.biz.storage.util.DerivedSpaceInfo;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Provides a {@link StorageEngine} which stores all objects in a bucket in a S3 compatible store.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class S3StorageEngine implements StorageEngine, Named {

    /**
     * Contains the name of the {@link ObjectStore} used to connect to the S3 compatible store.
     */
    public static final String CONFIG_KEY_LAYER1_STORE = "store";

    @Part
    private ObjectStores objectStores;

    private DerivedSpaceInfo<ObjectStore> stores = new DerivedSpaceInfo<>(CONFIG_KEY_LAYER1_STORE,
                                                                          StorageUtils.ConfigScope.LAYER1,
                                                                          this::filterByStorageEngine,
                                                                          this::resolveObjectStore);

    private boolean filterByStorageEngine(Extension ext) {
        return Strings.areEqual(ext.getString(ObjectStorage.CONFIG_KEY_LAYER1_ENGINE), getName());
    }

    private ObjectStore resolveObjectStore(Extension ext) {
        String storeName = ext.getString(CONFIG_KEY_LAYER1_STORE);

        if (!objectStores.isConfigured(storeName)) {
            StorageUtils.LOG.WARN(
                    "Layer 1/s3: Unknown or unconfigured object store '%s' used by space '%s'. Using system default.",
                    storeName,
                    ext.getId());
            return objectStores.store();
        }

        return objectStores.getStore(storeName);
    }

    @Nonnull
    @Override
    public String getName() {
        return "s3";
    }

    private BucketName bucketName(ObjectStore store, String bucket) {
        return store.getBucketName(bucket);
    }

    @Override
    public void storePhysicalObject(String space, String objectKey, InputStream data, long size) throws IOException {
        ObjectStore store = stores.get(space);
        Upload upload = store.uploadAsync(bucketName(store, space), objectKey, data, size);
        try {
            upload.waitForUploadResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 1/s3: Got interrupted while waiting for an upload to complete: %s/%s - %s (%s)",
                                    space,
                                    objectKey)
                            .handle();
        }
    }

    @Override
    public void storePhysicalObject(String space, String objectKey, File file) throws IOException {
        ObjectStore store = stores.get(space);
        store.upload(bucketName(store, space), objectKey, file);
    }

    @Override
    public void deletePhysicalObject(String space, String objectKey) throws IOException {
        ObjectStore store = stores.get(space);
        store.deleteObject(bucketName(store, space), objectKey);
    }

    @Override
    public void deliver(Response response,
                        String space,
                        String objectKey,
                        String fileExtension,
                        Consumer<Integer> failureHandler) throws IOException {
        ObjectStore store = stores.get(space);
        response.tunnel(store.objectUrl(bucketName(store, space), objectKey), failureHandler);
    }

    @Nullable
    @Override
    public FileHandle getData(String space, String objectKey) throws IOException {
        try {
            ObjectStore store = stores.get(space);
            return FileHandle.temporaryFileHandle(store.download(bucketName(store, space), objectKey));
        } catch (FileNotFoundException e) {
            Exceptions.ignore(e);
            return null;
        }
    }
}
