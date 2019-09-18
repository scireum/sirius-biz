/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import com.google.common.io.ByteStreams;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.util.DerivedSpaceInfo;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Provides a {@link StorageEngine} which operates on the local file system.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class FSStorageEngine implements StorageEngine, Named {

    /**
     * Contains the full path used to store objects.
     */
    public static final String CONFIG_KEY_LAYER1_PATH = "path";

    /**
     * Contains the base directory which will contain a sub directory per storage space.
     */
    public static final String CONFIG_KEY_LAYER1_BASE_DIR = "baseDir";

    @Part
    private StorageUtils utils;

    private DerivedSpaceInfo<File> baseDirs = new DerivedSpaceInfo<>(CONFIG_KEY_LAYER1_PATH,
                                                                     StorageUtils.ConfigScope.LAYER1,
                                                                     this::filterByStorageEngine,
                                                                     this::resolveEffectiveBaseDir);

    private boolean filterByStorageEngine(Extension ext) {
        return Strings.areEqual(ext.getString(ObjectStorage.CONFIG_KEY_LAYER1_ENGINE), getName());
    }

    private File resolveEffectiveBaseDir(Extension ext) {
        String path = ext.getString(CONFIG_KEY_LAYER1_PATH);
        if (Strings.isEmpty(path)) {
            path = ext.getString(CONFIG_KEY_LAYER1_BASE_DIR) + File.separator + ext.getId();
        }

        File root = new File(path);
        if (!root.exists()) {
            try {
                StorageUtils.LOG.INFO("Layer 1/fs: Base directory of space '%s' ('%s') does not exist. Creating...",
                                      ext.getId(),
                                      root);
                root.mkdirs();
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Layer 1/fs: Unable to resolve the base directory of space '%s' ('%s')"
                                        + " into a directory: %s (%s)!",
                                        ext.getId(),
                                        root)
                                .handle();
            }
        } else {
            if (!root.isDirectory()) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 1/fs: Unable to resolve the base directory of space '%s' ('%s')"
                                        + " into a directory: File exists but isn't a directory!",
                                        ext.getId(),
                                        root)
                                .handle();
            }
        }

        return root;
    }

    @Nonnull
    @Override
    public String getName() {
        return "fs";
    }

    private File getBaseDir(String bucket) {
        return baseDirs.get(bucket);
    }

    private File getFile(String bucket, String physicalKey) {
        String prefix = physicalKey.substring(0, 2);
        File parentDir = new File(getBaseDir(bucket), prefix);
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        return new File(parentDir, physicalKey);
    }

    @Override
    public void storePhysicalObject(String space, String objectKey, File file)
            throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            storePhysicalObject(space, objectKey, inputStream, file.length());
        }
    }

    @Override
    public void storePhysicalObject(String space,
                                    String objectKey,
                                    InputStream data,
                                    long size) throws IOException {
        File file = getFile(space, objectKey);
        try (FileOutputStream out = new FileOutputStream(file)) {
            ByteStreams.copy(data, out);
        }
    }

    @Override
    public void deletePhysicalObject(String space, String objectKey) throws IOException {
        if (Strings.isEmpty(objectKey)) {
            return;
        }

        File file = getFile(space, objectKey);
        if (!file.delete()) {
            throw new IOException(Strings.apply("Cannot delete: %s", file.getAbsolutePath()));
        }
    }

    @Override
    public void deliverAsDownload(WebContext ctx,
                                  String space,
                                  String objectKey,
                                  String filename,
                                  Consumer<Integer> failureHandler) throws IOException {
        File file = getFile(space, objectKey);

        if (file.isHidden() || !file.exists() || !file.isFile()) {
            failureHandler.accept(HttpResponseStatus.NOT_FOUND.code());
            return;
        }

        Response response = ctx.respondWith().infinitelyCached();
        response.download(filename);

        response.file(file);
    }

    @Override
    public void deliver(WebContext ctx,
                        String space,
                        String objectKey,
                        String fileExtension,
                        Consumer<Integer> failureHandler) throws IOException {
        File file = getFile(space, objectKey);

        if (file.isHidden() || !file.exists() || !file.isFile()) {
            failureHandler.accept(HttpResponseStatus.NOT_FOUND.code());
            return;
        }

        Response response = ctx.respondWith().infinitelyCached();
        response.named(objectKey + "." + fileExtension);

        response.file(file);
    }

    @Nullable
    @Override
    public FileHandle getData(String space, String objectKey) throws IOException {
        if (Strings.isEmpty(objectKey)) {
            return null;
        }

        File file = getFile(space, objectKey);
        if (file.exists()) {
            return FileHandle.permanentFileHandle(file);
        }

        return null;
    }
}
