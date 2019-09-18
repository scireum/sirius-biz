/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer1.FileHandle;
import sirius.web.http.WebContext;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Represents a backup copy of a {@link Blob}.
 */
public interface BlobRevision {

    /**
     * Returns the unique ID of this revision.
     *
     * @return the id of this revision
     */
    String getIdAsString();

    /**
     * Contains the physical key used by the {@link sirius.biz.storage.layer1.ObjectStorageSpace} to store the data.
     *
     * @return the layer 1 object key which contains the data of this revision
     */
    String getPhysicalObjectId();

    /**
     * Returns the timestamp when the revision was created.
     *
     * @return the creation timestamp of this revision
     */
    LocalDateTime getCreatedTimestamp();

    /**
     * Returns the size of the revision in bytes.
     *
     * @return the size in bytes
     */
    long getSize();

    /**
     * Provides a on-disk copy of the data associated with this blob
     *
     * @return a handle to the data of this blob
     */
    Optional<FileHandle> download();

    /**
     * Delivers the data of this blob into the given request.
     *
     * @param ctx the request to send a respond to
     */
    void deliver(WebContext ctx);

    /**
     * Delivers the data of this blob as a download into the given request.
     *
     * @param ctx the request to send a respond to
     */
    void deliverAsDownload(WebContext ctx);
}
