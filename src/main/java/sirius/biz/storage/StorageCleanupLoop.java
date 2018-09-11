/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import sirius.biz.protocol.TraceData;
import sirius.db.jdbc.OMA;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Removes old and outdated files from buckets.
 * <p>
 * A bucket can specify a max age for its objects. Older objects are automatically deleted by the system (vis this
 * loop).
 */
@Register(classes = BackgroundLoop.class, framework = Storage.FRAMEWORK_STORAGE)
public class StorageCleanupLoop extends BackgroundLoop {

    @Part
    private OMA oma;

    @Part
    private Storage storage;

    @Nonnull
    @Override
    public String getName() {
        return "storage-cleanup";
    }

    @Override
    protected double maxCallFrequency() {
        return 0.01f;
    }

    @Override
    protected String doWork() throws Exception {
        if (!oma.isReady()) {
            return null;
        }

        String temporaryUploadInfo = cleanupTemporaryUploads();
        String bucketInfo = cleanupBuckets();

        if (temporaryUploadInfo != null && bucketInfo != null) {
            return temporaryUploadInfo + ", " + bucketInfo;
        } else if (temporaryUploadInfo != null) {
            return temporaryUploadInfo;
        } else {
            return bucketInfo;
        }
    }

    /**
     * Deletes objects which were created for a {@link StoredObjectRef} but the entity containing the objects
     * was never saved.
     *
     * @see VirtualObject#TEMPORARY
     */
    private String cleanupTemporaryUploads() {
        List<VirtualObject> objectsToDelete = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.TEMPORARY, true)
                                                 .where(OMA.FILTERS.lt(VirtualObject.TRACE.inner(TraceData.CHANGED_AT),
                                                                       LocalDateTime.now().minusHours(1)))
                                                 .limit(256)
                                                 .queryList();
        if (objectsToDelete.isEmpty()) {
            return null;
        }

        for (VirtualObject obj : objectsToDelete) {
            storage.delete(obj);
        }

        return Strings.apply("Deleted %s temporary uploads", objectsToDelete.size());
    }

    private String cleanupBuckets() {
        StringBuilder logBuilder = new StringBuilder();
        for (BucketInfo bucket : storage.getBuckets()) {
            if (bucket.getDeleteFilesAfterDays() > 0) {
                cleanupBucket(bucket, logBuilder);
            }
        }

        if (logBuilder.length() > 0) {
            return logBuilder.toString();
        }

        return null;
    }

    private void cleanupBucket(BucketInfo bucket, StringBuilder logBuilder) {
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

            if (logBuilder.length() > 0) {
                logBuilder.append(", ");
            }

            logBuilder.append(Strings.apply("Deleted %s old files in '%s'", objectsToDelete.size(), bucket.getName()));
        }
    }
}
