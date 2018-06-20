/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.storage;

import com.google.common.io.ByteStreams;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.Response;
import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Provides a {@link PhysicalStorageEngine} which operates on the local file system.
 */
@Register(name = "fs")
public class FSStorageEngine implements PhysicalStorageEngine {

    @ConfigValue("storage.baseDir")
    private String baseDir;

    private File root;

    private File getBaseDir() {
        if (root == null) {
            root = new File(baseDir);
            if (!root.exists()) {
                try {
                    root.mkdirs();
                } catch (Exception e) {
                    Exceptions.handle(Storage.LOG, e);
                }
            } else {
                if (!root.isDirectory()) {
                    Exceptions.handle()
                              .to(Storage.LOG)
                              .withSystemErrorMessage(
                                      "The given base path '%s' for the object storage isn't a directory.",
                                      baseDir)
                              .handle();
                }
            }
        }

        return root;
    }

    @Override
    public void storePhysicalObject(String bucket, String physicalKey, InputStream data, String md5, long size)
            throws IOException {
        File file = getFile(bucket, physicalKey);
        try (FileOutputStream out = new FileOutputStream(file)) {
            ByteStreams.copy(data, out);
        }
    }

    private File getFile(String bucket, String physicalKey) {
        String prefix = physicalKey.substring(0, 2);
        File parentDir = new File(new File(getBaseDir(), bucket), prefix);
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        return new File(parentDir, physicalKey);
    }

    @Override
    public void deletePhysicalObject(String bucket, String physicalKey) {
        if (Strings.isEmpty(physicalKey)) {
            return;
        }

        File file = getFile(bucket, physicalKey);
        if (file.exists()) {
            if (!file.delete()) {
                Exceptions.handle()
                          .to(Storage.LOG)
                          .withSystemErrorMessage("Cannot delete: %s", file.getAbsolutePath())
                          .handle();
            }
        }
    }

    @Nullable
    @Override
    public InputStream getData(String bucket, String physicalKey) {
        File file = getFile(bucket, physicalKey);

        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            Exceptions.ignore(e);
            return null;
        }
    }

    @Override
    public void deliver(WebContext ctx, String bucket, String physicalKey, String fileExtension) {
        File file = getFile(bucket, physicalKey);
        Response response = ctx.respondWith().infinitelyCached();
        String name = ctx.get("name").asString();
        if (Strings.isFilled(name)) {
            response.download(name);
        } else {
            response.named(physicalKey + "." + fileExtension);
        }

        response.file(file);
    }

    @Override
    public String createURL(DownloadBuilder downloadBuilder) {
        return null;
    }
}
