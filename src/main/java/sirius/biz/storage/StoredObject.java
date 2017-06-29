/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import sirius.db.mixing.Entity;

import javax.annotation.Nullable;

/**
 * Represents the public accessible part of an object (file) stored by {@link Storage}.
 */
public interface StoredObject {

    /**
     * Represents the unique id of the stored object.
     */
    String getObjectKey();

    /**
     * Returns the reference to which this object belongs.
     *
     * @return the reference, preferable an {@link Entity#getUniqueName()} or <tt>null</tt> if there is no reference
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
