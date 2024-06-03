/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.mongo;

import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.ProcessBlobChangesLoop;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.Updater;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Implements processing actions on {@link MongoDirectory directories} and {@link MongoBlob blobs}.
 *
 * @see ProcessBlobChangesLoop
 */
@Register(framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
public class MongoProcessBlobChangesLoop extends ProcessBlobChangesLoop {

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @Override
    protected void deleteBlobs(Runnable counter) {
        buildBaseQuery(MongoBlob.class, query -> query.eq(MongoBlob.DELETED, true)).iterateAll(blob -> {
            try {
                deletePhysicalObject(blob);
                mango.delete(blob);
                counter.run();
            } catch (Exception exception) {
                handleBlobDeletionException(blob, exception);
            }
        });
    }

    @Override
    protected void deleteDirectories(Runnable counter) {
        buildBaseQuery(MongoDirectory.class, query -> query.eq(MongoDirectory.DELETED, true)).iterateAll(dir -> {
            try {
                propagateDelete(dir);
                mango.delete(dir);
                counter.run();
            } catch (Exception exception) {
                handleDirectoryDeletionException(dir, exception);
            }
        });
    }

    @Override
    protected void processCreatedBlobs(Runnable counter) {
        fetchAndProcessBlobs(query -> query.eq(MongoBlob.CREATED, true),
                             this::invokeCreatedHandlers,
                             updater -> updater.set(MongoBlob.CREATED, false),
                             counter);
    }

    @Override
    protected void processRenamedBlobs(Runnable counter) {
        // The created field may not exist in blobs created before this field was introduced.
        fetchAndProcessBlobs(query -> query.eq(MongoBlob.RENAMED, true).ne(MongoBlob.CREATED, true),
                             this::invokeRenamedHandlers,
                             updater -> updater.set(MongoBlob.RENAMED, false),
                             counter);
    }

    @Override
    protected void processContentUpdatedBlobs(Runnable counter) {
        // The created field may not exist in blobs created before this field was introduced.
        fetchAndProcessBlobs(query -> query.eq(MongoBlob.CONTENT_UPDATED, true).ne(MongoBlob.CREATED, true),
                             this::invokeContentUpdatedHandlers,
                             updater -> updater.set(MongoBlob.CONTENT_UPDATED, false),
                             counter);
    }

    @Override
    protected void propagateDelete(@Nonnull Directory dir) {
        String directoryId = dir.getIdAsString();
        mongo.update()
             .set(MongoDirectory.DELETED, true)
             .where(MongoDirectory.SPACE_NAME, dir.getSpaceName())
             .where(MongoDirectory.DELETED, false)
             .where(MongoDirectory.PARENT, directoryId)
             .executeForMany(MongoDirectory.class);
        mongo.update()
             .set(MongoBlob.DELETED, true)
             .where(MongoBlob.SPACE_NAME, dir.getSpaceName())
             .where(MongoBlob.DELETED, false)
             .where(MongoBlob.PARENT, directoryId)
             .executeForMany(MongoBlob.class);
    }

    @Override
    protected void processRenamedDirectories(Runnable counter) {
        buildBaseQuery(MongoDirectory.class, query -> query.eq(MongoDirectory.RENAMED, true)).iterateAll(dir -> {
            try {
                propagateRename(dir);
                mongo.update()
                     .set(MongoDirectory.RENAMED, false)
                     .where(MongoDirectory.ID, dir.getId())
                     .executeForOne(MongoDirectory.class);
                counter.run();
            } catch (Exception exception) {
                handleDirectoryRenameException(dir, exception);
            }
        });
    }

    @Override
    protected void propagateRename(@Nonnull Directory dir) {
        String directoryId = dir.getIdAsString();
        mongo.update()
             .set(MongoDirectory.RENAMED, true)
             .where(MongoDirectory.PARENT, directoryId)
             .executeForMany(MongoDirectory.class);
        mongo.update()
             .set(MongoBlob.PARENT_CHANGED, true)
             .where(MongoBlob.PARENT, directoryId)
             .executeForMany(MongoBlob.class);
    }

    @Override
    protected void processParentChangedBlobs(Runnable counter) {
        // The created field may not exist in blobs created before this field was introduced.
        fetchAndProcessBlobs(query -> query.eq(MongoBlob.PARENT_CHANGED, true).ne(MongoBlob.CREATED, true),
                             this::invokeParentChangedHandlers,
                             updater -> updater.set(MongoBlob.PARENT_CHANGED, false),
                             counter);
    }

    private void fetchAndProcessBlobs(Consumer<MongoQuery<MongoBlob>> filterExtender,
                                      Consumer<MongoBlob> blobConsumer,
                                      Consumer<Updater> updaterExtender,
                                      Runnable counter) {
        MongoQuery<MongoBlob> query = buildBaseQuery(MongoBlob.class, filterExtender);
        query.iterateAll(blob -> {
            blobConsumer.accept(blob);

            Updater updater = mongo.update();
            updaterExtender.accept(updater);
            updater.where(MongoBlob.ID, blob.getId()).executeForOne(MongoBlob.class);

            counter.run();
        });
    }

    private <E extends MongoEntity> MongoQuery<E> buildBaseQuery(Class<E> clazz,
                                                                 Consumer<MongoQuery<E>> filterExtender) {
        MongoQuery<E> query = mango.select(clazz);
        filterExtender.accept(query);
        return query.limit(CURSOR_LIMIT);
    }
}
