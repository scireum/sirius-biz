/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import javax.annotation.Nullable;
import java.time.LocalDateTime;

/**
 * Represents the public accessible part of an object (file) stored by {@link Storage}.
 */
public interface StoredObject {

    /**
     * Represents the unique id of the stored object.
     *
     * @return the unique key of the object
     */
    String getObjectKey();

    /**
     * Returns the filename of the object.
     *
     * @return the filename of the object
     */
    String getFilename();

    /**
     * Returns the size of the object in bytes.
     *
     * @return the size in bytes
     */
    long getFileSize();

    /**
     * Returns the timestamp when the entity was last modified.
     *
     * @return the last modification timestamp
     */
    LocalDateTime getLastModified();

    /**
     * Returns the reference to which this object belongs.
     *
     * @return the reference, preferable an {@link sirius.db.mixing.Entity#getUniqueName()}
     * or <tt>null</tt> if there is no reference
     */
    @Nullable
    String getReference();

    /**
     * Creates a builder to build a download URL for this object.
     *
     * @return a builder to create a download URL
     */
    DownloadBuilder prepareURL();
}
