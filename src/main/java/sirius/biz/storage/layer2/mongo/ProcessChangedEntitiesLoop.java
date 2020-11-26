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
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes {@link MongoBlob blobs} which have been marked as changed.
 * <p>
 * The actual processing is performed by the predefined {@link MongoBlobChangeHandler handlers}
 */
@Register(framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
public class ProcessChangedEntitiesLoop extends BackgroundLoop {

    private static final double FREQUENCY_EVERY_FIFTHEEN_SECONDS = 1 / 15d;

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @PriorityParts(MongoBlobChangeHandler.class)
    private List<MongoBlobChangeHandler> changeHandlers;

    @Nonnull
    @Override
    public String getName() {
        return "storage-layer2-change";
    }

    @Override
    public double maxCallFrequency() {
        return FREQUENCY_EVERY_FIFTHEEN_SECONDS;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        AtomicInteger numBlobs = processBlobs();

        if (numBlobs.get() == 0) {
            return null;
        }

        return Strings.apply("Processed %s blobs", numBlobs.get());
    }

    private AtomicInteger processBlobs() {
        AtomicInteger numBlobs = new AtomicInteger();
        mango.select(MongoBlob.class).eq(MongoBlob.CHANGED, true).limit(256).iterateAll(blob -> {
            try {
                changeHandlers.forEach(handler -> handler.execute(blob));
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
                     .set(MongoBlob.CHANGED, false)
                     .where(MongoBlob.ID, blob.getId())
                     .executeFor(MongoBlob.class);
            }
        });

        return numBlobs;
    }
}
