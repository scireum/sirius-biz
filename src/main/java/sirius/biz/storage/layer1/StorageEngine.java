/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Represents the actual physical storage layer for objects within buckets.
 */
@ParametersAreNonnullByDefault
public interface StorageEngine {

    /**
     * Stores the given data for the given key in the given bucket.
     *
     * @param space                the bucket to store the object in
     * @param objectKey            the physical storage key (a key is always only used once)
     * @param data                 the data to store
     * @param size                 the byte length of the data
     * @throws IOException in case of an IO error
     */
    void storePhysicalObject(String space,
                             String objectKey,
                             InputStream data,
                             long size) throws IOException;

    /**
     * Stores the given data for the given key in the given bucket.
     *
     * @param space                the bucket to store the object in
     * @param objectKey            the physical storage key (a key is always only used once)
     * @param file                 the data to store
     * @throws IOException in case of an IO error
     */
    void storePhysicalObject(String space, String objectKey, File file)
            throws IOException;

    /**
     * Deletes the physical object in the given bucket with the given id
     *
     * @param space     the bucket to delete the object from
     * @param objectKey the id of the object to delete
     * @throws IOException in case of an IO error
     */
    void deletePhysicalObject(String space, String objectKey) throws IOException;

    /**
     * Delivers the requested object to the given request as response.
     *
     * @param ctx            the request to provide a response for
     * @param space          the bucket of the object to deliver
     * @param objectKey      the id of the object to deliver
     * @param filename       the filename to use
     * @param failureHandler a handler which cann be invoked if the download cannot be performed.
     *                       This will be supplied with the HTTP error code.
     * @throws IOException in case of an IO error
     */
    void deliverAsDownload(WebContext ctx,
                           String space,
                           String objectKey,
                           String filename,
                           @Nullable Consumer<Integer> failureHandler) throws IOException;

    /**
     * Delivers the requested object to the given request as response.
     *
     * @param ctx            the request to provide a response for
     * @param space          the bucket of the object to deliver
     * @param objectKey      the id of the object to deliver
     * @param fileExtension  the file extension e.g. to setup a matching <tt>Content-Type</tt>
     * @param failureHandler a handler which cann be invoked if the download cannot be performed.
     *                       This will be supplied with the HTTP error code.
     * @throws IOException in case of an IO error
     */
    void deliver(WebContext ctx,
                 String space,
                 String objectKey,
                 String fileExtension,
                 @Nullable Consumer<Integer> failureHandler) throws IOException;

    /**
     * Downloads an provides the contents of the requested object.
     *
     * @param space     the bucket of the object
     * @param objectKey the id of the object
     * @return a handle to the given object or <tt>null</tt> if the object doesn't exist
     * @throws IOException in case of an IO error
     */
    @Nullable
    FileHandle getData(String space, String objectKey) throws IOException;
}
