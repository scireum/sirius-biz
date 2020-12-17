/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a S3 compatible object store which is commonly obtained via {@link ObjectStores}.
 */
public class ObjectStore {

    private static final String EXECUTOR_S3 = "s3";

    /**
     * When performing a multipart upload in {@link #upload(BucketName, String, InputStream)} we keep
     * a local aggregation buffer around to collect a large enough chunk to be uploaded to S3. This specifies the
     * initial size of this buffer.
     */
    private static final int INITIAL_LOCAL_AGGREGATION_BUFFER_SIZE = 32 * 1024;

    /**
     * Specifies the maximal size of the local aggregation buffer (as described in
     * {@link #INITIAL_LOCAL_AGGREGATION_BUFFER_SIZE}) before a chunk is uploaded to S3. Note that a single multipart
     * upload can at most consist of 10.000 parts - therefore this size limits the maximal total object size.
     */
    private static final int MAXIMAL_LOCAL_AGGREGATION_BUFFER_SIZE = 8 * 1024 * 1024;

    /**
     * When reading from an input stream into the local aggregation buffer (as described in
     * {@link #INITIAL_LOCAL_AGGREGATION_BUFFER_SIZE}), this specifies the size of the byte array used to shovel
     * chunks of data from the input stream into the aggregation buffer.
     */
    private static final int LOCAL_TRANSFER_BUFFER_SIZE = 8192;

    protected final ObjectStores stores;
    protected final String name;
    protected final AmazonS3 client;
    protected final TransferManager transferManager;
    protected final String bucketSuffix;

    @Part
    private static Tasks tasks;

    private class MonitoringProgressListener implements S3ProgressListener {

        private final Watch watch = Watch.start();
        private final boolean upload;

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
                watch.reset();
            }

            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
                if (upload) {
                    stores.uploads.addValue(watch.elapsedMillis());
                    stores.uploadedBytes.add(progressEvent.getBytesTransferred());
                } else {
                    stores.downloads.addValue(watch.elapsedMillis());
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

    protected ObjectStore(ObjectStores stores, String name, AmazonS3 client, String bucketSuffix) {
        this.stores = stores;
        this.name = name;
        this.client = client;
        this.transferManager = TransferManagerBuilder.standard()
                                                     .withExecutorFactory(() -> tasks.executorService(EXECUTOR_S3))
                                                     .withS3Client(client)
                                                     .build();

        if (Strings.isFilled(bucketSuffix) && bucketSuffix.contains(".") && !bucketSuffix.startsWith(".")) {
            ObjectStores.LOG.WARN(
                    "The bucketSuffix '%s' contains a '.' but does not start with one. This might lead to errors!",
                    bucketSuffix);
        }
        this.bucketSuffix = bucketSuffix;
    }

    /**
     * Provides access to the underlying S3 store.
     *
     * @return the client used to talk to the S3 store
     */
    public AmazonS3 getClient() {
        return client;
    }

    /**
     * Transforms the given bucket name into the effective name.
     *
     * @param bucket the bucket name to use
     * @return the effective bucket name (prefixes and suffixes applied if necessary)
     */
    public BucketName getBucketName(String bucket) {
        return new BucketName(bucket, bucketSuffix);
    }

    /**
     * Returns the effective bucket name for the given bucket and year.
     *
     * @param bucket the bucket name to use
     * @param year   the year to determine the name for
     * @return the effective bucket name (prefixes and suffixes applied if necessary)
     */
    public BucketName getBucketForYear(String bucket, int year) {
        return new BucketName(String.valueOf(year), bucket, bucketSuffix);
    }

    /**
     * Returns the effective bucket name for the given bucket and the current year.
     * <p>
     * This is a boilerplate for {@code getBucketForYear(bucket, LocalDate.now().getYear())}
     *
     * @param bucket the bucket name to use
     * @return the effective bucket name (prefixes and suffixes applied if necessary)
     */
    public BucketName getBucketForCurrentYear(String bucket) {
        LocalDate now = LocalDate.now();
        return getBucketForYear(bucket, now.getYear());
    }

    /**
     * Ensures that the given bucket exists.
     * <p>
     * The internal check is cached, therefore this method might be called frequently.
     *
     * @param bucket the bucket to check for
     */
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

    /**
     * Determines if the given bucket exists.
     * <p>
     * The internal check is cached, therefore this method might be called frequently.
     *
     * @param bucket the bucket to check for
     * @return <tt>true</tt> if the bucket exists, <tt>false</tt> otherwise
     */
    public boolean doesBucketExist(BucketName bucket) {
        Boolean cached = stores.bucketCache.get(Tuple.create(name, bucket.getName()));
        if (cached != null) {
            return cached;
        }
        boolean exists = this.checkExistence(bucket);
        if (exists) {
            stores.bucketCache.put(Tuple.create(name, bucket.getName()), exists);
        }
        return exists;
    }

    /**
     * Lists all known buckets (effective names).
     *
     * @return a list of all buckets in the given store
     */
    public List<String> listBuckets() {
        return getClient().listBuckets().stream().map(Bucket::getName).collect(Collectors.toList());
    }

    /**
     * Iterates of all objects in a bucket.
     * <p>
     * Keep in mind that a bucket might contain a very large amount of objects. This method must be used with care and
     * a lot of thought.
     *
     * @param bucket   the bucket to list objects for
     * @param prefix   the object name prefix used to filter
     * @param consumer the consumer to be supplied with each found object. As soon as <tt>false</tt> is returned,
     *                 the iteration stops.
     */
    public void listObjects(BucketName bucket, @Nullable String prefix, Predicate<S3ObjectSummary> consumer) {
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
                if (!consumer.test(obj) || !taskContext.isActive()) {
                    return;
                }
            }
        } while (objectListing.isTruncated() && taskContext.isActive());
    }

    /**
     * Deletes the given object.
     *
     * @param bucket   the bucket to delete the object from
     * @param objectId the object to delete
     */
    public void deleteObject(BucketName bucket, String objectId) {
        try {
            getClient().deleteObject(bucket.getName(), objectId);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete object %s from bucket %s - %s (%s)",
                                                    objectId,
                                                    bucket)
                            .handle();
        }
    }

    /**
     * Deletes the given bucket.
     * <p>
     * Not that this will most probably not delete objects and therefore only work on empty buckets.
     *
     * @param bucket the bucket to delete
     */
    public void deleteBucket(BucketName bucket) {
        try {
            getClient().deleteBucket(bucket.getName());
            stores.bucketCache.remove(Tuple.create(name, bucket.getName()));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete bucket %s - %s (%s)", bucket.getName())
                            .handle();
        }
    }

    private boolean checkExistence(BucketName bucket) {
        try {
            return client.doesBucketExistV2(bucket.getName());
        } catch (SdkClientException e) {
            Exceptions.handle()
                      .to(ObjectStores.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to check if %s exists: %s (%s)", bucket)
                      .handle();

            return false;
        }
    }

    /**
     * Generates a download URL for the given object.
     * <p>
     * This can be used to offer a public URL to retrieve the object's data.
     *
     * @param bucket   the bucket in which the object resides
     * @param objectId the object to generate the url for
     * @return a download URL for the object
     */
    public String url(BucketName bucket, String objectId) {
        return getClient().getUrl(bucket.getName(), objectId).toString();
    }

    /**
     * Generates a presigned download URL for the given object.
     * <p>
     * This can be used to {@link sirius.web.http.Response#tunnel(String)} the data to a client.
     *
     * @param bucket   the bucket in which the object resides
     * @param objectId the object to generate the url for
     * @return a presigned download URL for the object
     */
    public String objectUrl(BucketName bucket, String objectId) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket.getName(), objectId);
        return getClient().generatePresignedUrl(request).toString();
    }

    /**
     * Downloads the given object into a temporary file.
     * <p>
     * Make sure to delete the file once it has been processed.
     *
     * @param bucket   the bucket in which the object resides
     * @param objectId the object to download
     * @return the downloaded object as file
     * @throws FileNotFoundException if the requested object does not exist
     */
    public File download(BucketName bucket, String objectId) throws FileNotFoundException {
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
                            .withSystemErrorMessage(
                                    "Got interrupted while waiting for a download to complete: %s/%s - %s (%s)",
                                    bucket,
                                    objectId)
                            .handle();
        } catch (AmazonS3Exception e) {
            Files.delete(dest);
            if (e.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
                throw new FileNotFoundException(objectId);
            }

            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to download: %s/%s - %s (%s)",
                                                    bucket,
                                                    objectId)
                            .handle();
        } catch (Exception e) {
            Files.delete(dest);
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to download: %s/%s - %s (%s)",
                                                    bucket,
                                                    objectId)
                            .handle();
        }
    }

    /**
     * Asynchronously uploads the given file as an object.
     *
     * @param bucket   the bucket to upload the file to
     * @param objectId the object id to use
     * @param data     the data to upload
     * @return kind of a promise used to monitor the upload progress
     */
    public Upload uploadAsync(BucketName bucket, String objectId, File data) {
        return uploadAsync(bucket, objectId, data, null);
    }

    /**
     * Asynchronously uploads the given file as an object.
     *
     * @param bucket   the bucket to upload the file to
     * @param objectId the object id to use
     * @param data     the data to upload
     * @param metadata the metadata for the object
     * @return kind of a promise used to monitor the upload progress
     */
    public Upload uploadAsync(BucketName bucket, String objectId, File data, @Nullable ObjectMetadata metadata) {
        try {
            ensureBucketExists(bucket);
            return transferManager.upload(new PutObjectRequest(bucket.getName(), objectId, data).withMetadata(metadata),
                                          new MonitoringProgressListener(true));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to upload: %s/%s - %s (%s)",
                                                    bucket,
                                                    objectId)
                            .handle();
        }
    }

    /**
     * Synchronously uploads the given file.
     *
     * @param bucket   the bucket to upload the file to
     * @param objectId the object id to use
     * @param data     the data to upload
     */
    public void upload(BucketName bucket, String objectId, File data) {
        upload(bucket, objectId, data, null);
    }

    /**
     * Synchronously uploads the given file.
     *
     * @param bucket   the bucket to upload the file to
     * @param objectId the object id to use
     * @param data     the data to upload
     * @param metadata the metadata for the object
     */
    public void upload(BucketName bucket, String objectId, File data, @Nullable ObjectMetadata metadata) {
        try {
            uploadAsync(bucket, objectId, data, metadata).waitForUploadResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Got interrupted while waiting for an upload to complete: %s/%s - %s (%s)",
                                    bucket,
                                    objectId)
                            .handle();
        }
    }

    /**
     * Asynchronously uploads the given input stream as an object.
     *
     * @param bucket        the bucket to upload the file to
     * @param objectId      the object id to use
     * @param inputStream   the data to upload
     * @param contentLength the total number of bytes to upload
     * @return kind of a promise used to monitor the upload progress
     */
    public Upload uploadAsync(BucketName bucket, String objectId, InputStream inputStream, long contentLength) {
        return uploadAsync(bucket, objectId, inputStream, contentLength, null);
    }

    /**
     * Asynchronously uploads the given input stream as an object.
     *
     * @param bucket        the bucket to upload the file to
     * @param objectId      the object id to use
     * @param inputStream   the data to upload
     * @param contentLength the total number of bytes to upload
     * @param metadata      the metadata for the object
     * @return kind of a promise used to monitor the upload progress
     */
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
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket.getName(), objectId, inputStream, metadata);
            putObjectRequest.getRequestClientOptions().setReadLimit(MAXIMAL_LOCAL_AGGREGATION_BUFFER_SIZE);
            return transferManager.upload(putObjectRequest, new MonitoringProgressListener(true));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while trying to upload: %s/%s - %s (%s)",
                                                    bucket,
                                                    objectId)
                            .handle();
        }
    }

    /**
     * Synchronously uploads the given input stream as an object.
     *
     * @param bucket        the bucket to upload the file to
     * @param objectId      the object id to use
     * @param inputStream   the data to upload
     * @param contentLength the total number of bytes to upload
     */
    public void upload(BucketName bucket, String objectId, InputStream inputStream, long contentLength) {
        upload(bucket, objectId, inputStream, contentLength, null);
    }

    /**
     * Synchronously uploads the given input stream as an object.
     *
     * @param bucket        the bucket to upload the file to
     * @param objectId      the object id to use
     * @param inputStream   the data to upload
     * @param contentLength the total number of bytes to upload
     * @param metadata      the metadata for the object
     */
    public void upload(BucketName bucket,
                       String objectId,
                       InputStream inputStream,
                       long contentLength,
                       @Nullable ObjectMetadata metadata) {
        try {
            if (contentLength >= MAXIMAL_LOCAL_AGGREGATION_BUFFER_SIZE) {
                upload(bucket, objectId, inputStream);
            } else {
                uploadAsync(bucket, objectId, inputStream, contentLength, metadata).waitForUploadResult();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Got interrupted while waiting for an upload to complete: %s/%s - %s (%s)",
                                    bucket,
                                    objectId)
                            .handle();
        }
    }

    /**
     * Synchronously uploads the given input stream as an object.
     * <p>
     * If the total content-length is known in advance use {@link #upload(BucketName, String, InputStream, long)} which
     * migth use a more efficient API. If a file is to be uploaded use {@link #upload(BucketName, String, File)} which
     * can upload chunks in parallel.
     *
     * @param bucket      the bucket to upload the file to
     * @param objectId    the object id to use
     * @param inputStream the data to upload
     */
    public void upload(BucketName bucket, String objectId, InputStream inputStream) {
        ensureBucketExists(bucket);
        InitiateMultipartUploadResult multipartUpload =
                getClient().initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket.getName(), objectId));
        try {
            List<PartETag> etags = uploadInChunks(bucket, objectId, inputStream, multipartUpload.getUploadId());

            getClient().completeMultipartUpload(new CompleteMultipartUploadRequest(bucket.getName(),
                                                                                   objectId,
                                                                                   multipartUpload.getUploadId(),
                                                                                   etags));
        } catch (Exception e) {
            getClient().abortMultipartUpload(new AbortMultipartUploadRequest(bucket.getName(),
                                                                             objectId,
                                                                             multipartUpload.getUploadId()));
            throw Exceptions.handle()
                            .to(ObjectStores.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to perform a multipart upload for %s (%s): %s (%s)",
                                                    objectId,
                                                    bucket.getName())
                            .handle();
        }
    }

    @Nonnull
    protected List<PartETag> uploadInChunks(BucketName bucket,
                                            String objectId,
                                            InputStream inputStream,
                                            String multipartUploadId) throws IOException {
        List<PartETag> etags = new ArrayList<>();
        int partNumber = 1;

        ByteBuf localAggregationBuffer = Unpooled.buffer(INITIAL_LOCAL_AGGREGATION_BUFFER_SIZE);
        try {
            byte[] transferBuffer = new byte[LOCAL_TRANSFER_BUFFER_SIZE];
            int bytesRead = inputStream.read(transferBuffer);
            while (bytesRead > 0) {
                localAggregationBuffer.writeBytes(transferBuffer, 0, bytesRead);
                if (localAggregationBuffer.readableBytes() > MAXIMAL_LOCAL_AGGREGATION_BUFFER_SIZE) {
                    etags.add(uploadChunk(bucket, objectId, multipartUploadId, localAggregationBuffer, partNumber++));
                    localAggregationBuffer.clear();
                }

                bytesRead = inputStream.read(transferBuffer);
            }

            if (localAggregationBuffer.isReadable()) {
                etags.add(uploadChunk(bucket, objectId, multipartUploadId, localAggregationBuffer, partNumber++));
            }
        } finally {
            localAggregationBuffer.release();
        }

        return etags;
    }

    protected PartETag uploadChunk(BucketName bucket,
                                   String objectId,
                                   String multipartUploadId,
                                   ByteBuf localAggregationBuffer,
                                   int partNumber) {
        UploadPartRequest request = new UploadPartRequest().withBucketName(bucket.getName())
                                                           .withKey(objectId)
                                                           .withUploadId(multipartUploadId)
                                                           .withPartNumber(partNumber)
                                                           .withPartSize(localAggregationBuffer.readableBytes())
                                                           .withInputStream(new ByteBufInputStream(
                                                                   localAggregationBuffer));
        return getClient().uploadPart(request).getPartETag();
    }
}
