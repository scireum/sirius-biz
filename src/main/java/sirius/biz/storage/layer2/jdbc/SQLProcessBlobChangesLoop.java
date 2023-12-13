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
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.UpdateStatement;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Implements processing actions on {@link SQLDirectory directories} and {@link SQLBlob blobs}.
 */
@Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class SQLProcessBlobChangesLoop extends ProcessBlobChangesLoop {

    @Part
    private OMA oma;

    @Override
    protected void deleteBlobs(Runnable counter) {
        buildBaseQuery(SQLBlob.class, query -> query.eq(SQLBlob.DELETED, true)).streamBlockwise().forEach(blob -> {
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
        buildBaseQuery(SQLDirectory.class, query -> query.eq(SQLDirectory.DELETED, true)).streamBlockwise().forEach(dir -> {
            try {
                propagateDelete(dir);
                oma.delete(dir);
                counter.run();
            } catch (Exception e) {
                handleDirectoryDeletionException(dir, e);
            }
        });
    }

    @Override
    protected void processCreatedBlobs(Runnable counter) {
        fetchAndProcessBlobs(query -> query.eq(SQLBlob.CREATED, true),
                             this::invokeCreatedHandlers,
                             updater -> updater.set(SQLBlob.CREATED, false),
                             "not created",
                             counter);
    }

    @Override
    protected void processRenamedBlobs(Runnable counter) {
        fetchAndProcessBlobs(query -> query.eq(SQLBlob.RENAMED, true).eq(SQLBlob.CREATED, false),
                             this::invokeRenamedHandlers,
                             updater -> updater.set(SQLBlob.RENAMED, false),
                             "not renamed",
                             counter);
    }

    @Override
    protected void processContentUpdatedBlobs(Runnable counter) {
        fetchAndProcessBlobs(query -> query.eq(SQLBlob.CONTENT_UPDATED, true).eq(SQLBlob.CREATED, false),
                             this::invokeContentUpdatedHandlers,
                             updater -> updater.set(SQLBlob.CONTENT_UPDATED, false),
                             "not changed",
                             counter);
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
        buildBaseQuery(SQLDirectory.class, query -> query.eq(SQLDirectory.RENAMED, true)).streamBlockwise().forEach(dir -> {
            try {
                propagateRename(dir);
                oma.updateStatement(SQLDirectory.class)
                   .set(SQLDirectory.RENAMED, false)
                   .where(SQLDirectory.ID, dir.getId())
                   .executeUpdate();
                counter.run();
            } catch (Exception e) {
                handleDirectoryDeletionException(dir, e);
            }
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
        fetchAndProcessBlobs(query -> query.eq(SQLBlob.PARENT_CHANGED, true).eq(SQLBlob.CREATED, false),
                             this::invokeParentChangedHandlers,
                             updater -> updater.set(SQLBlob.PARENT_CHANGED, false),
                             "parent not changed",
                             counter);
    }

    private void fetchAndProcessBlobs(Consumer<SmartQuery<SQLBlob>> queryExtender,
                                      Consumer<SQLBlob> blobConsumer,
                                      Consumer<UpdateStatement> updaterExtender,
                                      String updateExceptionType,
                                      Runnable counter) {
        SmartQuery<SQLBlob> query = buildBaseQuery(SQLBlob.class, queryExtender);
        query.streamBlockwise().forEach(blob -> {
            blobConsumer.accept(blob);

            try {
                UpdateStatement update = oma.updateStatement(SQLBlob.class);
                updaterExtender.accept(update);
                update.where(SQLBlob.ID, blob.getId()).executeUpdate();

                counter.run();
            } catch (SQLException e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: Failed to reset blob %s (%s) in %s as %s: (%s)",
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName(),
                        updateExceptionType).handle();
            }
        });
    }

    private <E extends SQLEntity> SmartQuery<E> buildBaseQuery(Class<E> clazz, Consumer<SmartQuery<E>> filterExtender) {
        SmartQuery<E> query = oma.select(clazz);
        filterExtender.accept(query);
        return query.limit(CURSOR_LIMIT);
    }
}
