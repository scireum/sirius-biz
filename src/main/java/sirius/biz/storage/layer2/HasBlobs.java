/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

/**
 * Marker interface which can be applied to {@link sirius.db.mixing.BaseEntity entities} which contain a
 * {@link BlobContainer} composite.
 */
public interface HasBlobs {

    /**
     * Returns the blob container which manages attached blobs.
     *
     * @return the blob container which is in charge of managing attached blobs
     */
    BlobContainer getBlobContainer();
}
