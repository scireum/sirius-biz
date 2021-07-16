/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3;

import java.util.Objects;

/**
 * Represents an effective bucket name.
 * <p>
 * We commonly append a suffix to a bucket name or prepend a year before it. To better distinguish if a value at
 * hands is an internal short name of a bucket, or a fully qualified one, we use this marker class to represent
 * effective bucket names.
 *
 * @see ObjectStore#getBucketName(String)
 */
public class BucketName {

    private final String name;

    /**
     * Creates a new bucket name for the given bucket and suffix.
     *
     * @param name         the name of the bucket
     * @param bucketSuffix the suffix to append
     */
    public BucketName(String name, String bucketSuffix) {
        this.name = name + bucketSuffix;
    }

    /**
     * Creates a new bucket name for the given bucket, suffix and year.
     *
     * @param year         the year to prepend to the bucket
     * @param name         the name of the bucket
     * @param bucketSuffix the suffix to append
     */
    public BucketName(String year, String name, String bucketSuffix) {
        this.name = year + "." + name + bucketSuffix;
    }

    /**
     * Returns the effective name of the bucket.
     *
     * @return the effective name of the bucket to be used against the S3 API
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BucketName other)) {
            return false;
        }

        return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
