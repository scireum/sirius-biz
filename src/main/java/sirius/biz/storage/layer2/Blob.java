/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.kernel.health.HandledException;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Provides the metadata for a binary object.
 * <p>
 * The actual data will be stored by the layer 1 ({@link sirius.biz.storage.layer1.ObjectStorageSpace}) but the metadata
 * will be persisted in the appropriate database by a subclass of {@link BlobStorageSpace}.
 */
public interface Blob {

    /**
     * Returns the storage space which is in charge of managing this blob.
     *
     * @return the storage space in which this blob is stored
     */
    BlobStorageSpace getStorageSpace();

    /**
     * Returns the name of the {@link #getStorageSpace() storage space}.
     *
     * @return the name of the stroage space in which this directory resides.
     */
    String getSpaceName();

    /**
     * Returns the ID of the tenant for which this blob has been created.
     *
     * @return the id of the tenant which owns this blob
     */
    String getTenantId();

    /**
     * Returns the parent directory if this blob is associated to one.
     *
     * @return the parent directory or <tt>null</tt> if this blob isn't stored in a browsable location
     */
    @Nullable
    Directory getParent();

    /**
     * Represents the unique id of the blob.
     *
     * @return the unique key of the blob
     */
    String getBlobKey();

    /**
     * Contains the physical key used by the {@link sirius.biz.storage.layer1.ObjectStorageSpace} to store the current
     * data.
     *
     * @return the layer 1 object key which contains the current data of this blob
     */
    String getPhysicalObjectKey();

    /**
     * Returns the filename of the blob.
     *
     * @return the filename of the blob or <tt>null</tt> if no filename was assigned
     */
    @Nullable
    String getFilename();

    /**
     * Returns the full path to this blob (including its filename).
     *
     * @return the path to this blob or <tt>null</tt> if either there is no filename or no parent set
     */
    @Nullable
    String getPath();

    /**
     * Returns the size of the blob in bytes.
     *
     * @return the size in bytes
     */
    long getSize();

    /**
     * Returns the timestamp when the blob was last modified.
     *
     * @return the last modification timestamp
     */
    LocalDateTime getLastModified();

    /**
     * Returns time timestamp when the blob was last accessed.
     *
     * @return the last access timestamp
     */
    @Nullable
    LocalDateTime getLastTouched();

    /**
     * Provides a on-disk copy of the data associated with this blob
     *
     * @return a handle to the data of this blob
     */
    Optional<FileHandle> download();

    /**
     * Delivers the data of this blob into the given HTTP response.
     *
     * @param response the HTTP response to populate
     */
    void deliver(Response response);

    /**
     * Determines if this blob is still marked as temporary.
     *
     * @return <tt>true</tt> if the blob is temporary, <tt>false</tt> otherwise
     */
    boolean isTemporary();

    /**
     * Marks the blob as "read accessed".
     * <p>
     * Note that in nearly all circumstances, this isn't needed to be invoked manually as the framework will
     * track access.
     */
    void touch();

    /**
     * Deletes the blob.
     */
    void delete();

    /**
     * Moves this blob into a new directory.
     *
     * @param newParent the new directory to move the blob to. Note that if <tt>null</tt> is passed in, a directory was
     *                  probably selected which was known to be incompatible - therefore the method is expected
     *                  to throw an appropriate exception.
     * @throws HandledException if the blob cannot be moved into the given directory (different space etc).
     */
    void move(@Nullable Directory newParent);

    /**
     * Renames this blob.
     *
     * @param newName the new name to use
     * @throws HandledException if the blob cannot be renamed
     */
    void rename(String newName);

    /**
     * Provides new content for this blob.
     *
     * @param filename the new filename to use (if given)
     * @param file     the file providing the new data to use
     */
    void updateContent(@Nullable String filename, File file);

    /**
     * Provides new content for this blob.
     * <p>
     * Also note that if a file is used to provide the new contents of this blob, use
     * {@link #updateContent(String, File)} as this is likely way more efficient.
     * <p>
     * Note that this blob object itself will also be updated with the appropriate metadata
     * (filename, size, lastModified).
     *
     * @param filename      the new filename to use (if given)
     * @param data          the stream providing the data to use
     * @param contentLength the exact number of bytes which will be provided by data
     * @see #updateContent(String, File)
     */
    void updateContent(@Nullable String filename, InputStream data, long contentLength);

    /**
     * Creates a output stream which can be used to update the contents of this blob.
     * <p>
     * The data written into the output stream will be buffered locally and used to update the contents once the
     * stream is closed. Use {@link #updateContent(String, File)} if a file is used to update the contents or
     * {@link #updateContent(String, InputStream, long)} if a stream with known length is used. Both methods
     * are likely to be way more efficient as no local buffer is required.
     *
     * @param filename the new filename to use (if given)
     * @return an output stream which can be used to update the contents of this blob
     * @see #updateContent(String, File)
     * @see #updateContent(String, InputStream, long)
     */
    OutputStream createOutputStream(@Nullable String filename);

    /**
     * Creates a output stream which can be used to update the contents of this blob.
     * <p>
     * Stores the contents once the stream is closed just like {@link #createOutputStream(String)} but permits to add a callback
     * which is invoked once the blob has been updated.
     *
     * @param completeCallback a handler which is invoked once the stream is closed and the underlying blob has been
     *                         updated
     * @param filename         the new filename to use (if given)
     * @return an output stream which can be used to update the contents of this blob
     * @see #updateContent(String, File)
     * @see #updateContent(String, InputStream, long)
     */
    OutputStream createOutputStream(Runnable completeCallback, @Nullable String filename);

    /**
     * Creates an input stream which can be used to read the contents of this blob.
     * <p>
     * Note that this might require downloading the blob from an external storage device into a local buffer. When
     * responding to a HTTP request use {@link #deliver(Response)} which might be more efficient in this case. Use
     * {@link #download()} to have full control over the downloaded file handle.
     *
     * @return an input stream providing the contents of this blob
     * @see #download()
     * @see #deliver(Response)
     */
    InputStream createInputStream();

    /**
     * Lists all known variants of this blob.
     * <p>
     * A variant is a converted version of the original blob (e.g. a resized JPG of a given EPS).
     *
     * @return a list of all known variants
     */
    List<? extends BlobVariant> fetchVariants();

    /**
     * Tries to find the variant with the given name.
     * <p>
     * This will check if a variant with the given name exists and return it. If no such variant exists,
     * an empty optional is returned.
     *
     * @param name the name of the variant to lookup
     * @return the variant with the given name, or an empty optional if none was found
     */
    Optional<BlobVariant> findVariant(String name);

    /**
     * Provides a builder which can be used to create a delivery or download link.
     *
     * @return a builder to create a download or delivery URL
     */
    URLBuilder url();
}
