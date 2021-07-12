/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Represents a derived variant of a {@link Blob}.
 * <p>
 * This could be a resized JPG derived from a given EPS file.
 */
public interface BlobVariant {

    /**
     * Returns the unique ID of this revision.
     *
     * @return the id of this revision
     */
    String getIdAsString();

    /**
     * Contains the variant designator which was used to derive this variant.
     *
     * @return the variant designator
     */
    String getVariantName();

    /**
     * Contains the physical key used by the {@link sirius.biz.storage.layer1.ObjectStorageSpace} to store the data.
     *
     * @return the layer 1 object key which contains the data of this revision
     */
    String getPhysicalObjectKey();

    /**
     * Returns the timestamp of the last conversion attempt.
     *
     * @return the timestamp when a conversion was last attempted
     */
    LocalDateTime getLastConversionAttempt();

    /**
     * Returns the size of the revision in bytes.
     *
     * @return the size in bytes
     */
    long getSize();

    /**
     * Provides an on-disk copy of the data associated with this blob
     *
     * @return a handle to the data of this blob
     */
    Optional<FileHandle> download();

    /**
     * Determines if a conversion is currently in progress.
     *
     * @return <tt>true</tt> if a node is currently trying to perform the requested conversion
     */
    boolean isQueuedForConversion();

    /**
     * The number of conversion attempts which have already been attempted.
     *
     * @return the number of conversion attempts
     */
    int getNumAttempts();

    /**
     * Deletes this variant.
     */
    void delete();
}
