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
        mango.select(MongoBlob.class).eq(MongoBlob.DELETED, true).limit(256).iterateAll(blob -> {
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
        mango.select(MongoDirectory.class).eq(MongoDirectory.DELETED, true).limit(256).iterateAll(dir -> {
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
    protected void processCreatedOrRenamedBlobs(Runnable counter) {
        mango.select(MongoBlob.class).eq(MongoBlob.CREATED_OR_RENAMED, true).limit(256).iterateAll(blob -> {
            invokeChangedOrDeletedHandlers(blob);
            mongo.update()
                 .set(MongoBlob.CREATED_OR_RENAMED, false)
                 .where(MongoBlob.ID, blob.getId())
                 .executeFor(MongoBlob.class);
            counter.run();
        });
    }

    @Override
    protected void propagateDelete(@Nonnull Directory dir) {
        String directoryId = dir.getIdAsString();
        mongo.update()
             .set(MongoDirectory.DELETED, true)
             .where(MongoDirectory.PARENT, directoryId)
             .executeFor(MongoDirectory.class);
        mongo.update().set(MongoBlob.DELETED, true).where(MongoBlob.PARENT, directoryId).executeFor(MongoBlob.class);
    }
}
