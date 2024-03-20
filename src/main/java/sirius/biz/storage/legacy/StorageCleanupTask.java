/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.biz.protocol.TraceData;
import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.db.jdbc.OMA;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.timer.EveryDay;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Removes old and outdated files from buckets.
 * <p>
 * A bucket can specify a max age for its objects. Older objects are automatically deleted by the system (vis this
 * loop).
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
@Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class StorageCleanupTask implements EveryDay {

    @Part
    private OMA oma;

    @Part
    private Storage storage;

    @Part
    private Tasks tasks;

    @Override
    public String getConfigKeyName() {
        return "storage-cleaner";
    }

    @Override
    public void runTimer() throws Exception {
        if (!oma.isReady()) {
            return;
        }
        tasks.defaultExecutor().start(this::runCleanup);
    }

    protected void runCleanup() {
        try {
            cleanupTemporaryUploads();
            cleanupBuckets();
        } catch (Exception exception) {
            Exceptions.handle(Log.BACKGROUND, exception);
        }
    }

    /**
     * Deletes objects which were created for a {@link StoredObjectRef} but the entity containing the objects
     * was never saved.
     *
     * @see VirtualObject#TEMPORARY
     */
    private void cleanupTemporaryUploads() {
        List<VirtualObject> objectsToDelete = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.TEMPORARY, true)
                                                 .where(OMA.FILTERS.lt(VirtualObject.TRACE.inner(TraceData.CHANGED_AT),
                                                                       LocalDateTime.now().minusHours(1)))
                                                 .limit(256)
                                                 .queryList();
        if (objectsToDelete.isEmpty()) {
            return;
        }

        for (VirtualObject obj : objectsToDelete) {
            storage.delete(obj);
        }
    }

    private void cleanupBuckets() {
        for (BucketInfo bucket : storage.getBuckets()) {
            if (bucket.getDeleteFilesAfterDays() > 0) {
                cleanupBucket(bucket);
            }
        }
    }

    private void cleanupBucket(BucketInfo bucket) {
        LocalDate limit = LocalDate.now().minusDays(bucket.getDeleteFilesAfterDays());
        List<VirtualObject> objectsToDelete = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.BUCKET, bucket.getName())
                                                 .where(OMA.FILTERS.lt(VirtualObject.TRACE.inner(TraceData.CHANGED_AT),
                                                                       limit))
                                                 .limit(256)
                                                 .queryList();
        if (!objectsToDelete.isEmpty()) {
            for (VirtualObject obj : objectsToDelete) {
                storage.delete(obj);
            }
        }
    }
}
