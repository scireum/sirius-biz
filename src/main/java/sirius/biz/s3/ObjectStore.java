/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.Operation;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ObjectStore {

    private static final String EXECUTOR_S3 = "s3";

    protected final ObjectStores stores;
    protected final String name;
    protected final AmazonS3Client client;
    protected final TransferManager transferManager;
    protected final String bucketSuffix;

    @Part
    private static Tasks tasks;

    private class MonitoringProgressListener implements S3ProgressListener {

        private Watch w = Watch.start();
        private boolean upload;

        MonitoringProgressListener(boolean upload) {
            this.upload = upload;
        }

        @Override
        public void onPersistableTransfer(PersistableTransfer persistableTransfer) {
            // NOOP
        }

        @Override
        public void progressChanged(ProgressEvent progressEvent) {
            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_STARTED_EVENT) {
                w.reset();
            }

            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
                if (upload) {
                    stores.uploads.addValue(w.elapsedMillis());
                    stores.uploadedBytes.add(progressEvent.getBytesTransferred());
                } else {
                    stores.downloads.addValue(w.elapsedMillis());
                    stores.downloadedBytes.add(progressEvent.getBytesTransferred());
                }
            }

            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_FAILED_EVENT) {
                if (upload) {
                    stores.failedUploads.inc();
                } else {
                    stores.failedDownloads.inc();
                }
            }
        }
    }

    public ObjectStore(ObjectStores stores, String name, AmazonS3Client client, String bucketSuffix) {
        this.stores = stores;
        this.name = name;
        this.client = client;
        this.transferManager = TransferManagerBuilder.standard()
                                                     .withExecutorFactory(() -> tasks.executorService(EXECUTOR_S3))
                                                     .withS3Client(client)
                                                     .build();

        if (Strings.isFilled(bucketSuffix) && !bucketSuffix.startsWith(".")) {
            this.bucketSuffix = "." + bucketSuffix;
        } else {
            this.bucketSuffix = bucketSuffix;
        }
    }

    /**
     * Provides access to the underlying S3 store.
     *
     * @return the client used to talk to the S3 store
     */
    public AmazonS3Client getClient() {
        return client;
    }

    public BucketName getBucketName(String bucket) {
        return new BucketName(bucket, bucketSuffix);
    }

    public BucketName getBucketForYear(String bucket, int year) {
        return new BucketName(String.valueOf(year), bucket, bucketSuffix);
    }

    public BucketName getBucketForCurrentYear(String bucket) {
        LocalDate now = LocalDate.now();
        return getBucketForYear(bucket, now.getYear());
    }

    public void ensureBucketExists(BucketName bucket) {
        if (!doesBucketExist(bucket)) {
            try {
                client.createBucket(bucket.getName());
            } catch (Exception e) {
                Exceptions.handle()
                          .to(ObjectStores.LOG)
                          .error(e)
                          .withSystemErrorMessage("Failed to create: %s - %s (%s)", bucket)
                          .handle();
            }
        }
    }

    public boolean doesBucketExist(BucketName bucket) {
        return stores.bucketCache.get(Tuple.create(name, bucket.getName()), this::checkExistence);
    }

    public List<String> listBuckets() {
        return getClient().listBuckets().stream().map(Bucket::getName).collect(Collectors.toList());
    }

    public void listObjects(BucketName bucket, @Nullable String prefix, Function<S3ObjectSummary, Boolean> consumer) {
        ObjectListing objectListing = null;
        TaskContext taskContext = CallContext.getCurrent().get(TaskContext.class);

        do {
            try (Operation op = new Operation(() -> Strings.apply("Fetching S3 objects from %s (prefix: %s)",
                                                                  bucket.getName(),
                                                                  prefix), Duration.ofSeconds(10))) {
                if (objectListing != null) {
                    objectListing = getClient().listNextBatchOfObjects(objectListing);
                } else {
                    objectListing = getClient().listObjects(bucket.getName(), prefix);
                }
            }

            for (S3ObjectSummary obj : objectListing.getObjectSummaries()) {
                if (!consumer.apply(obj) || !taskContext.isActive()) {
                    return;
                }
            }
        } while (objectListing.isTruncated() && taskContext.isActive());
    }

    public void deleteObject(BucketName bucket, String objectId) {
        try {
            getClient().deleteObject(bucket.getName(), objectId);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete object %s from bucket %s - %s (%s)",
                                                    bucket.getName(),
                                                    objectId)
                            .handle();
        }
    }

    public void deleteBucket(BucketName bucket) {
        try {
            getClient().deleteBucket(bucket.getName());
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete bucket %s - %s (%s)", bucket.getName())
                            .handle();
        }
    }

    private Boolean checkExistence(Tuple<String, String> storeAndBucket) {
        try {
            return client.doesBucketExist(storeAndBucket.getSecond());
        } catch (SdkClientException e) {
            Exceptions.handle()
                      .to(ObjectStores.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to check if %s exists: %s (%s)", storeAndBucket.getSecond())
                      .handle();

            return false;
        }
    }

    public String objectUrl(BucketName bucket, String objectId) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket.getName(), objectId);
        return getClient().generatePresignedUrl(request).toString();
    }

    public Upload uploadAsync(BucketName bucket, String objectId, File data, @Nullable ObjectMetadata metadata) {
        try {
            ensureBucketExists(bucket);
            return transferManager.upload(new PutObjectRequest(bucket.getName(), objectId, data).withMetadata(metadata),
                                          new MonitoringProgressListener(true));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to upload: %s/%s -",
                                                    bucket.getName(),
                                                    objectId)
                            .handle();
        }
    }

    public void upload(BucketName bucket, String objectId, File data, @Nullable ObjectMetadata metadata) {
        try {
            uploadAsync(bucket, objectId, data, metadata).waitForUploadResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Got interrupted while waiting for an upload to complete: %s/%s - %s (%s)")
                            .handle();
        }
    }

    public File download(BucketName bucket, String objectId) {
        File dest = null;
        try {
            dest = File.createTempFile("AMZS3", null);
            transferManager.download(new GetObjectRequest(bucket.getName(), objectId),
                                     dest,
                                     new MonitoringProgressListener(false)).waitForCompletion();
            return dest;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.delete(dest);

            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Got interrupted while waiting for a download to complete:%s/%s -",
                                                    bucket.getName(),
                                                    objectId)
                            .handle();
        } catch (Exception e) {
            Files.delete(dest);

            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to download: %s/%s -",
                                                    bucket.getName(),
                                                    objectId)
                            .handle();
        }
    }

    public Upload uploadAsync(BucketName bucket,
                              String objectId,
                              InputStream inputStream,
                              long contentLength,
                              @Nullable ObjectMetadata metadata) {
        if (metadata == null) {
            metadata = new ObjectMetadata();
        }

        metadata.setContentLength(contentLength);
        try {
            ensureBucketExists(bucket);
            return transferManager.upload(new PutObjectRequest(bucket.getName(), objectId, inputStream, metadata),
                                          new MonitoringProgressListener(true));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to upload: %s/%s -",
                                                    bucket.getName(),
                                                    objectId)
                            .handle();
        }
    }

    public void upload(BucketName bucket,
                       String objectId,
                       InputStream inputStream,
                       long contentLength,
                       @Nullable ObjectMetadata metadata) {
        try {
            uploadAsync(bucket, objectId, inputStream, contentLength, metadata).waitForUploadResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Got interrupted while waiting for an upload to complete: %s (%s)")
                            .handle();
        }
    }
}
