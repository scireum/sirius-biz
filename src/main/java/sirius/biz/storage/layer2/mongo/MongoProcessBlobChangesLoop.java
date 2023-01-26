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
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

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
        mango.select(MongoBlob.class).eq(MongoBlob.DELETED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            try {
                deletePhysicalObject(blob);
                counter.run();
                mango.delete(blob);
            } catch (Exception e) {
                handleBlobDeletionException(blob, e);
            }
        });
    }

    @Override
    protected void deleteDirectories(Runnable counter) {
        mango.select(MongoDirectory.class).eq(MongoDirectory.DELETED, true).limit(CURSOR_LIMIT).iterateAll(dir -> {
            try {
                propagateDelete(dir);
                mango.delete(dir);
            } catch (Exception e) {
                handleDirectoryDeletionException(dir, e);
            }
            counter.run();
        });
    }

    @Override
    protected void processCreatedBlobs(Runnable counter) {
        mango.select(MongoBlob.class).eq(MongoBlob.CREATED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            invokeCreatedHandlers(blob);
            mongo.update()
                 .set(MongoBlob.CREATED, false)
                 .where(MongoBlob.ID, blob.getId())
                 .executeForOne(MongoBlob.class);
            counter.run();
        });
    }

    @Override
    protected void processRenamedBlobs(Runnable counter) {
        mango.select(MongoBlob.class)
             .eq(MongoBlob.RENAMED, true)
             .eq(MongoBlob.CREATED, false)
             .limit(CURSOR_LIMIT)
             .iterateAll(blob -> {
                 invokeRenamedHandlers(blob);
                 mongo.update()
                      .set(MongoBlob.RENAMED, false)
                      .where(MongoBlob.ID, blob.getId())
                      .executeForOne(MongoBlob.class);
                 counter.run();
             });
    }

    @Override
    protected void processContentUpdatedBlobs(Runnable counter) {
        mango.select(MongoBlob.class)
             .eq(MongoBlob.CONTENT_UPDATED, true)
             .eq(MongoBlob.CREATED, false)
             .limit(CURSOR_LIMIT)
             .iterateAll(blob -> {
                 invokeContentUpdatedHandlers(blob);
                 mongo.update()
                      .set(MongoBlob.CONTENT_UPDATED, false)
                      .where(MongoBlob.ID, blob.getId())
                      .executeForOne(MongoBlob.class);
                 counter.run();
             });
    }

    @Override
    protected void propagateDelete(@Nonnull Directory dir) {
        String directoryId = dir.getIdAsString();
        mongo.update()
             .set(MongoDirectory.DELETED, true)
             .where(MongoDirectory.PARENT, directoryId)
             .executeForMany(MongoDirectory.class);
        mongo.update()
             .set(MongoBlob.DELETED, true)
             .where(MongoBlob.PARENT, directoryId)
             .executeForMany(MongoBlob.class);
    }

    @Override
    protected void processRenamedDirectories(Runnable counter) {
        mango.select(MongoDirectory.class).eq(MongoDirectory.RENAMED, true).limit(CURSOR_LIMIT).iterateAll(dir -> {
            try {
                propagateRename(dir);
                mongo.update()
                     .set(MongoDirectory.RENAMED, false)
                     .where(MongoDirectory.ID, dir.getId())
                     .executeForOne(MongoDirectory.class);
            } catch (Exception e) {
                handleDirectoryRenameException(dir, e);
            }
            counter.run();
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
        mango.select(MongoBlob.class).eq(MongoBlob.PARENT_CHANGED, true).limit(CURSOR_LIMIT).iterateAll(blob -> {
            invokeParentChangedHandlers(blob);
            mongo.update()
                 .set(MongoBlob.PARENT_CHANGED, false)
                 .where(MongoBlob.ID, blob.getId())
                 .executeForOne(MongoBlob.class);
            counter.run();
        });
    }
}
