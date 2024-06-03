/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents the actual physical storage layer for objects within buckets.
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
public interface PhysicalStorageEngine {

    /**
     * Stores the given data for the given key in the given bucket.
     *
     * @param bucket      the bucket to store the object in
     * @param physicalKey the physical storage key (a key is always only used once)
     * @param data        the data to store
     * @param md5         the MD5 checksum of the data
     * @param size        the byte length of the data
     * @throws IOException in case of an IO error
     */
    void storePhysicalObject(String bucket, String physicalKey, InputStream data, String md5, long size)
            throws IOException;

    /**
     * Deletes the physical object in the given bucket with the given id
     *
     * @param bucket      the bucket to delete the object from
     * @param physicalKey the id of the object to delete
     */
    void deletePhysicalObject(String bucket, String physicalKey);

    /**
     * Delivers the requested object to the given request as response.
     *
     * @param webContext           the request to provide a response for
     * @param bucket        the bucket of the object to deliver
     * @param physicalKey   the id of the object to deliver
     * @param fileExtension the file extension e.g. to setup a matching <tt>Content-Type</tt>
     */
    void deliver(WebContext webContext, String bucket, String physicalKey, String fileExtension);

    /**
     * Can provide a custom download URL for a given builder.
     *
     * @param downloadBuilder the builder specifying the download parameters
     * @return an URL to download the specified object or <tt>null</tt> to use a generic URL
     */
    @Nullable
    String createURL(DownloadBuilder downloadBuilder);

    /**
     * Downloads an provides the contents of the requested object.
     *
     * @param bucket      the bucket of the object
     * @param physicalKey the id of the object
     * @return a stream providing the contents of the object or <tt>null</tt> if the object doesn't exist
     */
    @Nullable
    InputStream getData(String bucket, String physicalKey);
}
