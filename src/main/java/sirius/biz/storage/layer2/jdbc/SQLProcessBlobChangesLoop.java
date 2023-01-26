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
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.sql.SQLException;

/**
 * Implements processing actions on {@link SQLDirectory directories} and {@link SQLBlob blobs}.
 */
@Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class SQLProcessBlobChangesLoop extends ProcessBlobChangesLoop {

    @Part
    private OMA oma;

    @Override
    protected void deleteBlobs(Runnable counter) {
        oma.select(SQLBlob.class).eq(SQLBlob.DELETED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            try {
                deletePhysicalObject(blob);
                oma.delete(blob);
                counter.run();
            } catch (Exception e) {
                handleBlobDeletionException(blob, e);
            }
        });
    }

    @Override
    protected void deleteDirectories(Runnable counter) {
        oma.select(SQLDirectory.class).eq(SQLDirectory.DELETED, true).limit(CURSOR_LIMIT).iterateAll(dir -> {
            try {
                propagateDelete(dir);
                oma.delete(dir);
            } catch (Exception e) {
                handleDirectoryDeletionException(dir, e);
            }
            counter.run();
        });
    }

    @Override
    protected void processCreatedOrRenamedBlobs(Runnable counter) {
        oma.select(SQLBlob.class).eq(SQLBlob.CREATED_OR_RENAMED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            invokeCreatedOrRenamedHandlers(blob);
            try {
                oma.updateStatement(SQLBlob.class)
                   .set(SQLBlob.CREATED_OR_RENAMED, false)
                   .where(SQLBlob.ID, blob.getId())
                   .executeUpdate();
            } catch (SQLException e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: Failed to reset blob %s (%s) in %s as not changed: (%s)",
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName()).handle();
            }
            counter.run();
        });
    }

    @Override
    protected void processCreatedBlobs(Runnable counter) {
        oma.select(SQLBlob.class).eq(SQLBlob.CREATED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            invokeCreatedHandlers(blob);
            try {
                oma.updateStatement(SQLBlob.class)
                   .set(SQLBlob.CREATED, false)
                   .where(SQLBlob.ID, blob.getId())
                   .executeUpdate();
            } catch (SQLException e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: Failed to reset blob %s (%s) in %s as not created: (%s)",
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName()).handle();
            }
            counter.run();
        });
    }

    @Override
    protected void processRenamedBlobs(Runnable counter) {
        oma.select(SQLBlob.class).eq(SQLBlob.RENAMED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            invokeRenamedHandlers(blob);
            try {
                oma.updateStatement(SQLBlob.class)
                   .set(SQLBlob.RENAMED, false)
                   .where(SQLBlob.ID, blob.getId())
                   .executeUpdate();
            } catch (SQLException e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: Failed to reset blob %s (%s) in %s as not renamed: (%s)",
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName()).handle();
            }
            counter.run();
        });
    }

    @Override
    protected void propagateDelete(Directory dir) {
        Long directoryId = ((SQLDirectory) dir).getId();
        try {
            oma.updateStatement(SQLDirectory.class)
               .set(SQLDirectory.DELETED, true)
               .set(SQLDirectory.PARENT, null)
               .where(SQLDirectory.PARENT, directoryId)
               .executeUpdate();
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.DELETED, true)
               .set(SQLBlob.PARENT, null)
               .where(SQLBlob.PARENT, directoryId)
               .executeUpdate();
        } catch (SQLException e) {
            buildStorageException(e).withSystemErrorMessage(
                    "Layer 2: Failed to propagate deletion for directory %s (%s) in %s: (%s)",
                    directoryId,
                    dir.getName(),
                    dir.getSpaceName()).handle();
        }
    }

    @Override
    protected void processRenamedDirectories(Runnable counter) {
        oma.select(SQLDirectory.class).eq(SQLDirectory.RENAMED, true).limit(CURSOR_LIMIT).iterateAll(dir -> {
            try {
                propagateRename(dir);
                oma.updateStatement(SQLDirectory.class)
                   .set(SQLDirectory.RENAMED, false)
                   .where(SQLDirectory.ID, dir.getId())
                   .executeUpdate();
            } catch (Exception e) {
                handleDirectoryDeletionException(dir, e);
            }
            counter.run();
        });
    }

    @Override
    protected void propagateRename(Directory dir) {
        Long directoryId = ((SQLDirectory) dir).getId();
        try {
            oma.updateStatement(SQLDirectory.class)
               .set(SQLDirectory.RENAMED, true)
               .where(SQLDirectory.PARENT, directoryId)
               .executeUpdate();
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.PARENT_CHANGED, true)
               .where(SQLBlob.PARENT, directoryId)
               .executeUpdate();
        } catch (SQLException e) {
            buildStorageException(e).withSystemErrorMessage(
                    "Layer 2: Failed to propagate rename for directory %s (%s) in %s: (%s)",
                    directoryId,
                    dir.getName(),
                    dir.getSpaceName()).handle();
        }
    }

    @Override
    protected void processParentChangedBlobs(Runnable counter) {
        oma.select(SQLBlob.class).eq(SQLBlob.PARENT_CHANGED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            invokeParentChangedHandlers(blob);
            try {
                oma.updateStatement(SQLBlob.class)
                   .set(SQLBlob.PARENT_CHANGED, false)
                   .where(SQLBlob.ID, blob.getId())
                   .executeUpdate();
            } catch (SQLException e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: Failed to reset blob %s (%s) in %s as parent not changed: (%s)",
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName()).handle();
            }
            counter.run();
        });
    }
}
