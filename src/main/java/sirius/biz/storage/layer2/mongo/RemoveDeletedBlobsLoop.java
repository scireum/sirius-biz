/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.mongo;

import sirius.biz.storage.util.StorageUtils;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finally deletes {@link MongoDirectory directories} and {@link MongoBlob blobs} which have been marked as deleted.
 */
@Register(framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
public class RemoveDeletedBlobsLoop extends BackgroundLoop {

    private static final double FREQUENCY_EVERY_FIFTEEN_SECONDS = 1 / 15d;

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

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
                          .withSystemErrorMessage(
                                  "Layer 2/Mongo: Failed to finally delete the blob %s (%s) in %s: %s (%s)",
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
        mango.select(MongoDirectory.class).eq(MongoDirectory.DELETED, true).limit(256).iterateAll(dir -> {
            try {
                propagateDelete(dir);
                mango.delete(dir);
            } catch (Exception e) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(e)
                          .withSystemErrorMessage(
                                  "Layer 2/Mongo: Failed to finally delete the directory %s (%s) in %s: %s (%s)",
                                  dir.getId(),
                                  dir.getName(),
                                  dir.getSpaceName())
                          .handle();
            }
            numDirectories.incrementAndGet();
        });

        return numDirectories;
    }

    private void propagateDelete(MongoDirectory dir) {
        mongo.update()
             .set(MongoDirectory.DELETED, true)
             .where(MongoDirectory.PARENT, dir.getId())
             .executeFor(MongoDirectory.class);
        mongo.update().set(MongoBlob.DELETED, true).where(MongoBlob.PARENT, dir.getId()).executeFor(MongoBlob.class);
    }
}
