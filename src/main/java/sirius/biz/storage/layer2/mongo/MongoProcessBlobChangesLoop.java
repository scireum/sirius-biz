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
import sirius.biz.storage.util.StorageUtils;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.util.concurrent.atomic.AtomicInteger;

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
    protected AtomicInteger deleteBlobs() {
        AtomicInteger numBlobs = new AtomicInteger();
        mango.select(MongoBlob.class).eq(MongoBlob.DELETED, true).limit(256).iterateAll(blob -> {
            try {
                if (Strings.isFilled(blob.getPhysicalObjectKey())) {
                    blob.getStorageSpace().getPhysicalSpace().delete(blob.getPhysicalObjectKey());
                }

                mango.delete(blob);
                numBlobs.incrementAndGet();
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage("Layer 2/Mongo: Failed to finally delete the blob %s (%s) in %s: (%s)",
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
        mango.select(MongoDirectory.class).eq(MongoDirectory.DELETED, true).limit(256).iterateAll(dir -> {
            try {
                propagateDelete(dir);
                mango.delete(dir);
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage(
                                  "Layer 2/Mongo: Failed to finally delete the directory %s (%s) in %s: (%s)",
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
        mango.select(MongoBlob.class).eq(MongoBlob.CREATED_OR_RENAMED, true).limit(256).iterateAll(blob -> {
            try {
                createdOrRenamedHandlers.forEach(handler -> handler.execute(blob));
                numBlobs.incrementAndGet();
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage(
                                  "Layer 2/Mongo: Failed to process the changed blob %s (%s) in %s: %s (%s)",
                                  blob.getBlobKey(),
                                  blob.getFilename(),
                                  blob.getSpaceName())
                          .handle();
            } finally {
                mongo.update()
                     .set(MongoBlob.CREATED_OR_RENAMED, false)
                     .where(MongoBlob.ID, blob.getId())
                     .executeFor(MongoBlob.class);
            }
        });

        return numBlobs;
    }

    @Override
    protected void propagateDelete(Directory dir) {
        String directoryId = ((MongoDirectory) dir).getId();
        mongo.update()
             .set(MongoDirectory.DELETED, true)
             .where(MongoDirectory.PARENT, directoryId)
             .executeFor(MongoDirectory.class);
        mongo.update().set(MongoBlob.DELETED, true).where(MongoBlob.PARENT, directoryId).executeFor(MongoBlob.class);
    }
}
