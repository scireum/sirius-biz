/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.ProcessBlobChangesLoop;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements processing actions on {@link SQLDirectory directories} and {@link SQLBlob blobs}.
 */
@Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class SQLProcessBlobChangesLoop extends ProcessBlobChangesLoop {

    @Part
    private OMA oma;

    @Override
    protected AtomicInteger deleteBlobs() {
        AtomicInteger numBlobs = new AtomicInteger();
        oma.select(SQLBlob.class).eq(SQLBlob.DELETED, true).limit(256).iterateAll(blob -> {
            try {
                deletePhysicalObject(blob);

                oma.delete(blob);
                numBlobs.incrementAndGet();
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage("Layer 2/SQL: Failed to finally delete the blob %s (%s) in %s: (%s)",
                                                  blob.getBlobKey(),
                                                  blob.getFilename(),
                                                  blob.getSpaceName())
                          .handle();
            }
        });

        return numBlobs;
    }

    @Override
    protected AtomicInteger deleteDirectories() {
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
                                  "Layer 2/SQL: Failed to finally delete the directory %s (%s) in %s: (%s)",
                                  dir.getId(),
                                  dir.getName(),
                                  dir.getSpaceName())
                          .handle();
            }
            numDirectories.incrementAndGet();
        });

        return numDirectories;
    }

    @Override
    protected AtomicInteger processCreatedOrRenamedBlobs() {
        AtomicInteger numBlobs = new AtomicInteger();
        oma.select(SQLBlob.class).eq(SQLBlob.CREATED_OR_RENAMED, true).limit(256).iterateAll(blob -> {
            try {
                createdOrRenamedHandlers.forEach(handler -> handler.execute(blob));
                numBlobs.incrementAndGet();
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage("Layer 2/SQL: Failed to process the blob %s (%s) in %s: (%s)",
                                                  blob.getBlobKey(),
                                                  blob.getFilename(),
                                                  blob.getSpaceName())
                          .handle();
            } finally {
                try {
                    oma.updateStatement(SQLBlob.class)
                       .set(SQLBlob.CREATED_OR_RENAMED, false)
                       .where(SQLBlob.ID, blob.getId())
                       .executeUpdate();
                } catch (SQLException e) {
                    Exceptions.handle()
                              .to(StorageUtils.LOG)
                              .error(e)
                              .withSystemErrorMessage(
                                      "Layer 2/SQL: Failed to reset blob %s (%s) in %s as not changed: (%s)",
                                      blob.getBlobKey(),
                                      blob.getFilename(),
                                      blob.getSpaceName())
                              .handle();
                }
            }
        });

        return numBlobs;
    }

    @Override
    protected void propagateDelete(Directory dir) {
        Long directoryId = ((SQLDirectory) dir).getId();
        try {
            oma.updateStatement(SQLDirectory.class)
               .set(SQLDirectory.DELETED, true)
               .where(SQLDirectory.PARENT, directoryId)
               .executeUpdate();
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.DELETED, true)
               .where(SQLBlob.PARENT, directoryId)
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: Failed to propagate deletion for directory %s (%s) in %s: (%s)",
                              directoryId,
                              dir.getName(),
                              dir.getSpaceName())
                      .handle();
        }
    }
}
