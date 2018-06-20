/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.storage.vfs;

import sirius.biz.jdbc.storage.BucketInfo;
import sirius.biz.jdbc.storage.Storage;
import sirius.biz.jdbc.storage.StoredObject;
import sirius.biz.jdbc.tenants.Tenant;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;

import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Makes the work directory visible within the {@link VirtualFileSystem}.
 */
@Register
public class WorkRoot implements VFSRoot {

    private static final String WORK = "work";
    private static final int MAX_FILES_IN_FTP = 250;

    @Part
    private Storage storage;

    @Override
    public void collectRootFolders(VirtualFile parent, Consumer<VirtualFile> fileCollector) {
        BucketInfo bucket = storage.getBucket(WORK).orElse(null);

        if (bucket == null || !UserContext.getCurrentUser().hasPermission(bucket.getPermission())) {
            return;
        }

        VirtualFile workDir = createWorkDir(parent, bucket);
        fileCollector.accept(workDir);
    }

    private VirtualFile createWorkDir(VirtualFile parent, BucketInfo bucket) {
        VirtualFile workDir = new VirtualFile(parent, WORK);
        if (bucket.isCanCreate()) {
            workDir.withCreateFileHandler(name -> {
                StoredObject newFile =
                        storage.findOrCreateObjectByPath(UserContext.getCurrentUser().as(Tenant.class), WORK, name);
                return storage.updateFile(newFile);
            });
        }

        workDir.withChildren(this::listChildren);
        return workDir;
    }

    private void listChildren(VirtualFile parent, Consumer<VirtualFile> childCollector) {
        BucketInfo bucket = storage.getBucket(WORK).orElse(null);

        AtomicInteger maxFiles = new AtomicInteger(MAX_FILES_IN_FTP);
        storage.list(bucket, UserContext.getCurrentUser().as(Tenant.class), file -> {
            childCollector.accept(transform(parent, file));
            return maxFiles.decrementAndGet() > 0;
        });
    }

    private VirtualFile transform(VirtualFile parent, StoredObject file) {
        VirtualFile result = new VirtualFile(parent, file.getFilename());
        result.withSize(file.getFileSize());
        result.withLastModified(file.getLastModified().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        result.withInputStreamSupplier(() -> storage.getData(file));
        result.withOutputStreamSupplier(() -> storage.updateFile(file));
        result.withDeleteHandler(() -> {
            storage.delete(file);
            return true;
        });

        return result;
    }
}
