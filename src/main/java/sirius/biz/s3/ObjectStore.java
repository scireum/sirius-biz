/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.health.Exceptions;

import java.time.LocalDate;

public class ObjectStore {

    protected final AmazonS3Client client;
    protected final String bucketSuffix;
    //TODO bad idea
    private final Cache<String, Boolean> bucketCache = CacheManager.createCoherentCache("storage-buckets");

    public ObjectStore(AmazonS3Client client, String bucketSuffix) {
        this.client = client;
        this.bucketSuffix = bucketSuffix;
    }

    /**
     * Provides access to the underlying S3 store.
     *
     * @return the client used to talk to the S3 store
     */
    public AmazonS3Client getClient() {
        return client;
    }

    public String getBucketName(String bucket) {
        return bucket + bucketSuffix;
    }

    public String getBucketForYear(String bucket) {
        LocalDate now = LocalDate.now();
        return now.getYear() + "." + getBucketName(bucket);
    }

    public void checkBucket(String effectiveBucketName) {
        boolean doesBucketExist = bucketCache.get(effectiveBucketName, this::checkExistence);

        if (!doesBucketExist) {
            try {
                getClient().createBucket(effectiveBucketName);
            } catch (SdkClientException e) {
                Exceptions.handle(e);
            }
        }
    }

    private Boolean checkExistence(String effectiveBucketName) {
        try {
            return getClient().doesBucketExist(effectiveBucketName);
        } catch (SdkClientException e) {
            Exceptions.handle(e);
            return false;
        }
    }
}
