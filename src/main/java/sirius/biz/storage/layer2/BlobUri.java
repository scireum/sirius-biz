/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

/**
 * Represents a URI as generated by {@link URLBuilder}.
 */
public class BlobUri {

    private boolean largeFileExpected;
    private boolean physical;
    private boolean download;
    private boolean cacheable;
    private String storageSpace;
    private String accessToken;
    private String blobKey;
    private String physicalKey;
    private String variant;
    private String filename;

    /**
     * Signals that a very large file is expected
     *
     * @param largeFileExpected <tt>true</tt> if a large file is expected, <tt>false</tt> otherwise
     * @return the current instance for fluent method calls
     */
    public BlobUri withLargeFileExpected(boolean largeFileExpected) {
        this.largeFileExpected = largeFileExpected;
        return this;
    }

    /**
     * Signals that the physical file should be delivered.
     *
     * @param physical <tt>true</tt> if the physical file should be delivered, <tt>false</tt> otherwise
     * @return the current instance for fluent method calls
     */
    public BlobUri withPhysical(boolean physical) {
        this.physical = physical;
        return this;
    }

    /**
     * Signals that the file should be delivered as download.
     *
     * @param download <tt>true</tt> if the file should be delivered as download, <tt>false</tt> otherwise
     * @return the current instance for fluent method calls
     */
    public BlobUri withDownload(boolean download) {
        this.download = download;
        return this;
    }

    /**
     * Signals that HTTP caching should be supported.
     *
     * @param cacheable <tt>true</tt> if HTTP caching should be supported, <tt>false</tt> otherwise
     * @return the current instance for fluent method calls
     */
    public BlobUri withCacheable(boolean cacheable) {
        this.cacheable = cacheable;
        return this;
    }

    /**
     * Sets the space which is accessed
     *
     * @param storageSpace the space to access
     * @return the current instance for fluent method calls
     */
    public BlobUri withStorageSpace(String storageSpace) {
        this.storageSpace = storageSpace;
        return this;
    }

    /**
     * Sets the security token to verify.
     *
     * @param accessToken the token to verify
     * @return the current instance for fluent method calls
     */
    public BlobUri withAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    /**
     * Sets the blob object key used to determine which {@link Blob} should be delivered.
     *
     * @param blobKey the key of the blob to deliver
     * @return the current instance for fluent method calls
     */
    public BlobUri withBlobKey(String blobKey) {
        this.blobKey = blobKey;
        return this;
    }

    /**
     * Sets the physical key used to determine which physical object should be delivered.
     *
     * @param physicalKey the key of the physical object to deliver
     * @return the current instance for fluent method calls
     */
    public BlobUri withPhysicalKey(String physicalKey) {
        this.physicalKey = physicalKey;
        return this;
    }

    /**
     * Sets the variant to deliver.
     *
     * @param variant the variant to deliver
     * @return the current instance for fluent method calls
     */
    public BlobUri withVariant(String variant) {
        this.variant = variant;
        return this;
    }

    /**
     * Sets the filename to deliver.
     *
     * @param filename the filename to deliver
     * @return the current instance for fluent method calls
     */
    public BlobUri withFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public boolean isLargeFileExpected() {
        return largeFileExpected;
    }

    public boolean isPhysical() {
        return physical;
    }

    public boolean isDownload() {
        return download;
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public String getStorageSpace() {
        return storageSpace;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getBlobKey() {
        return blobKey;
    }

    public String getPhysicalKey() {
        return physicalKey;
    }

    public String getVariant() {
        return variant;
    }

    public String getFilename() {
        return filename;
    }
}
