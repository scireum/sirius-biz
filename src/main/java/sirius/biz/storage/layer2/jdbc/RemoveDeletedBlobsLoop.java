/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finally deletes {@link SQLDirectory directories} and {@link SQLBlob blobs} which have been marked as deleted.
 */
@Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class RemoveDeletedBlobsLoop extends BackgroundLoop {

    private static final double FREQUENCY_EVERY_FIFTEEN_SECONDS = 1 / 15d;

    @Part
    private OMA oma;

    @Nonnull
    @Override
    public String getName() {
        return "storage-layer2-blob-delete";
    }

    @Override
    public double maxCallFrequency() {
        return FREQUENCY_EVERY_FIFTEEN_SECONDS;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        AtomicInteger numDirectories = deleteDirectories();
        AtomicInteger numBlobs = deleteBlobs();

        if (numDirectories.get() == 0 && numBlobs.get() == 0) {
            return null;
        }

        return Strings.apply("Deleted %s directories and %s blobs", numDirectories.get(), numBlobs.get());
    }

    private AtomicInteger deleteBlobs() {
        AtomicInteger numBlobs = new AtomicInteger();
        oma.select(SQLBlob.class).eq(SQLBlob.DELETED, true).limit(256).iterateAll(blob -> {
            try {
                if (Strings.isFilled(blob.getPhysicalObjectKey())) {
                    blob.getStorageSpace().getPhysicalSpace().delete(blob.getPhysicalObjectKey());
                }

                oma.delete(blob);
                numBlobs.incrementAndGet();
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage(
                                  "Layer 2/SQL: Failed to finally delete the blob %s (%s) in %s: %s (%s)",
                                  blob.getBlobKey(),
                                  blob.getFilename(),
                                  blob.getSpaceName())
                          .handle();
            }
        });

        return numBlobs;
    }

    private AtomicInteger deleteDirectories() {
        AtomicInteger numDirectories = new AtomicInteger();
        oma.select(SQLDirectory.class).eq(SQLDirectory.DELETED, true).limit(256).iterateAll(dir -> {
            try {
                propagateDelete(dir);
                oma.delete(dir);
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage(
                                  "Layer 2/SQL: Failed to finally delete the directory %s (%s) in %s: %s (%s)",
                                  dir.getId(),
                                  dir.getName(),
                                  dir.getSpaceName())
                          .handle();
            }
            numDirectories.incrementAndGet();
        });

        return numDirectories;
    }

    private void propagateDelete(SQLDirectory dir) throws SQLException {
        oma.updateStatement(SQLDirectory.class)
           .set(SQLDirectory.DELETED, true)
           .where(SQLDirectory.PARENT, dir.getId())
           .executeUpdate();
        oma.updateStatement(SQLBlob.class)
           .set(SQLBlob.DELETED, true)
           .where(SQLBlob.PARENT, dir.getId())
           .executeUpdate();
    }
}
