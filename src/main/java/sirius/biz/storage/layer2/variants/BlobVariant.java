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

import javax.annotation.Nullable;
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
     * Determines if the conversion has ultimately failed, or the max number of attempts has been reached.
     *
     * @return <tt>true</tt> if the conversion is considered failed, <tt>false</tt> otherwise
     */
    boolean isFailed();

    /**
     * Returns the amount of time in which the conversion tool ran.
     *
     * @return the conversion duration in milliseconds
     */
    long getConversionDuration();

    /**
     * Returns the amount of time, the conversion task spent in the queue, waiting for a free thread.
     *
     * @return the queue duration in milliseconds
     */
    long getQueueDuration();

    /**
     * Returns the amount of time, the system spent down- and uploading the conversion in- and outputs.
     *
     * @return the transfer duration in milliseconds
     */
    long getTransferDuration();

    /**
     * Deletes this variant.
     */
    void delete();

    /**
     * Returns the checksum of the variant if available.
     *
     * @return the checksum of the variant or <tt>null</tt> if no checksum is available
     */
    @Nullable
    String getCheckSum();
}
