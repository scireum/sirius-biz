/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.layer1.transformer.ByteBlockTransformer;
import sirius.biz.storage.layer1.transformer.TransformingInputStream;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Provides a {@link ObjectStorageSpace} which operates on the local file system.
 */
public class FSObjectStorageSpace extends ObjectStorageSpace {

    /**
     * Contains the full path used to store objects.
     */
    public static final String CONFIG_KEY_LAYER1_PATH = "path";

    /**
     * Contains the base directory which will contain a sub directory per storage space.
     */
    public static final String CONFIG_KEY_LAYER1_BASE_DIR = "baseDir";

    @Part
    private static StorageUtils utils;

    private File baseDir;

    /**
     * Creates a new instance based on the given config.
     *
     * @param name      the name of the space to create
     * @param extension the configuration to create the space for
     */
    protected FSObjectStorageSpace(String name, Extension extension) throws Exception {
        super(name, extension);
        this.baseDir = resolveEffectiveBaseDir(extension);
    }

    private File resolveEffectiveBaseDir(Extension extension) {
        String path = extension.getString(CONFIG_KEY_LAYER1_PATH);
        if (Strings.isEmpty(path)) {
            path = extension.getString(CONFIG_KEY_LAYER1_BASE_DIR) + File.separator + extension.getId();
        }

        File root = new File(path);
        if (!root.exists()) {
            try {
                StorageUtils.LOG.INFO("Layer 1/fs: Base directory of space '%s' ('%s') does not exist. Creating...",
                                      extension.getId(),
                                      root);
                root.mkdirs();
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Layer 1/fs: Unable to resolve the base directory of space '%s' ('%s')"
                                        + " into a directory: %s (%s)!",
                                        extension.getId(),
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
                                        extension.getId(),
                                        root)
                                .handle();
            }
        }

        return root;
    }

    private File getFile(String physicalKey) {
        String prefix = physicalKey.substring(0, 2);
        File parentDir = new File(baseDir, prefix);
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        return new File(parentDir, physicalKey);
    }

    @Override
    protected void storePhysicalObject(String objectKey, File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            storePhysicalObject(objectKey, inputStream, file.length());
        }
    }

    @Override
    protected void storePhysicalObject(String objectKey, File file, ByteBlockTransformer transformer)
            throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            storePhysicalObject(objectKey, inputStream, transformer);
        }
    }

    @Override
    protected void storePhysicalObject(String objectKey, InputStream data, long size) throws IOException {
        File file = getFile(objectKey);
        try (FileOutputStream out = new FileOutputStream(file)) {
            Streams.transfer(data, out);
        }
    }

    @Override
    protected void storePhysicalObject(String objectKey, InputStream data, ByteBlockTransformer transformer)
            throws IOException {
        File file = getFile(objectKey);
        try (FileOutputStream out = new FileOutputStream(file);
             TransformingInputStream in = new TransformingInputStream(data, transformer)) {
            Streams.transfer(in, out);
        }
    }

    @Override
    protected void deletePhysicalObject(String objectKey) throws IOException {
        if (Strings.isEmpty(objectKey)) {
            return;
        }

        File file = getFile(objectKey);
        Files.delete(file.toPath());
    }

    @Override
    protected void deliverPhysicalObject(Response response, String objectKey, IntConsumer failureHandler)
            throws IOException {
        File file = getFile(objectKey);

        if (file.isHidden() || !file.exists() || !file.isFile()) {
            failureHandler.accept(HttpResponseStatus.NOT_FOUND.code());
            return;
        }

        response.file(file);
    }

    @Override
    protected void deliverPhysicalObject(Response response,
                                         String objectKey,
                                         ByteBlockTransformer transformer,
                                         @Nullable IntConsumer failureHandler) throws IOException {
        File file = getFile(objectKey);

        if (file.isHidden() || !file.exists() || !file.isFile()) {
            failureHandler.accept(HttpResponseStatus.NOT_FOUND.code());
            return;
        }

        try (OutputStream out = response.outputStream(HttpResponseStatus.OK, null);
             InputStream in = getAsStream(objectKey, transformer)) {
            Streams.transfer(in, out);
        }
    }

    @Nullable
    @Override
    protected FileHandle getData(String objectKey) throws IOException {
        if (Strings.isEmpty(objectKey)) {
            return null;
        }

        File file = getFile(objectKey);
        if (file.exists()) {
            return FileHandle.permanentFileHandle(file);
        }

        return null;
    }

    @Nullable
    @Override
    protected FileHandle getData(String objectKey, ByteBlockTransformer transformer) throws IOException {
        File dest = null;
        try {
            dest = File.createTempFile("FSTRANSFORM", null);
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
                                    "Layer 1/fs: An error occurred while trying to download: %s/%s - %s (%s)",
                                    name,
                                    objectKey)
                            .handle();
        }
    }

    @Override
    @Nullable
    protected InputStream getAsStream(String objectKey) throws IOException {
        if (Strings.isEmpty(objectKey)) {
            return null;
        }

        File file = getFile(objectKey);
        if (file.exists()) {
            return new FileInputStream(file);
        }

        return null;
    }

    @Nullable
    @Override
    protected InputStream getAsStream(String objectKey, ByteBlockTransformer transformer) throws IOException {
        if (Strings.isEmpty(objectKey)) {
            return null;
        }

        File file = getFile(objectKey);
        if (file.exists()) {
            return new TransformingInputStream(new FileInputStream(file), transformer);
        }

        return null;
    }

    @Override
    public void iterateObjects(Predicate<String> physicalKeyHandler) throws IOException {
        java.nio.file.Files.walkFileTree(baseDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toFile().isFile()) {
                    if (physicalKeyHandler.test(file.toFile().getName())) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.TERMINATE;
                    }
                }
                return super.visitFile(file, attrs);
            }
        });
    }
}
