/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Represents a reference to a {@link Blob} which can be placed as field within an {@link BaseEntity}.
 * <p>
 * Being a hard reference the lifetime of the blob is bound to the containing entity. If the entity is deleted,
 * the blob is deleted as well.
 */
public class BlobHardRef {

    protected final String space;
    protected String fallbackUri;
    protected Blob blob;
    protected String key;

    @Part
    @Nullable
    protected static BlobStorage storage;

    /**
     * Creates a new reference for the given space.
     *
     * @param space the space to place referenced objects in
     */
    public BlobHardRef(String space) {
        this.space = space;
    }

    /**
     * Permits to specify a fallback URI to use in case the reference is empty.
     * <p>
     * This URI will be passed along to the {@link URLBuilder} when {@link #url()} is invoked.
     *
     * @param fallbackUri the relative fallback URI to use
     * @return the reference itself for fluent method calls
     * @see URLBuilder#withFallbackUri(String)
     * @see URLBuilder#safeBuildURL(String)
     * @see URLBuilder#buildImageURL()
     */
    public BlobHardRef withFallbackUri(String fallbackUri) {
        this.fallbackUri = fallbackUri;
        return this;
    }

    /**
     * Retrieves the actual referenced blob from the storage layer.
     *
     * @return the referenced blob or <tt>null</tt> if there is no referenced blob
     */
    @Nullable
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public Blob getBlob() {
        if (blob == null && Strings.isFilled(key)) {
            blob = storage.getSpace(space).findByBlobKey(key).orElse(null);
            if (blob == null) {
                key = null;
            }
        }
        return blob;
    }

    /**
     * Fetches the underlying blob.
     *
     * @return the blob just like {@link #getBlob()} but returns an empty optional instead of <tt>null</tt> if
     * the reference is empty.
     */
    public Optional<Blob> fetchBlob() {
        return Optional.ofNullable(getBlob());
    }

    /**
     * Directly fetches the file size of the referenced blob.
     *
     * @return the file size of the blob in bytes or 0 if the reference is empty
     */
    public long fetchSize() {
        return fetchBlob().map(Blob::getSize).orElse(0L);
    }

    /**
     * Returns the formatted file size of the referenced blob.
     *
     * @return the formatted (human-readable) file size of the referenced blob or an empty string, if the reference
     * is empty
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String fetchFormattedSize() {
        return fetchBlob().map(Blob::getSize).map(NLS::formatSize).orElse("");
    }

    /**
     * Assigns a blob to be referenced.
     *
     * @param blob the blob to be referenced
     */
    public void setBlob(Blob blob) {
        this.blob = blob;
        if (blob == null) {
            this.key = null;
        } else {
            this.key = blob.getBlobKey();
        }
    }

    /**
     * Specifies an object key to reference.
     *
     * @param key the key of the object to reference
     */
    public void setKey(String key) {
        if (Strings.isEmpty(key)) {
            this.key = null;
            this.blob = null;
        } else {
            this.key = key;
            if (this.blob != null && !Strings.areEqual(this.blob.getBlobKey(), key)) {
                this.blob = null;
            }
        }
    }

    /**
     * Clears the reference.
     * <p>
     * This is a boilerplate for calling {@code ref.setKey(null)}.
     */
    public void clear() {
        setKey(null);
    }

    /**
     * Determines the filename of the referenced blob.
     *
     * @return the filename, or <tt>null</tt> if either no blob or one without a filename is referenced
     */
    @Nullable
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getFilename() {
        if (isEmpty() || getBlob() == null) {
            return null;
        }

        return getBlob().getFilename();
    }

    /**
     * Determines the path of the referenced blob.
     *
     * @return the path, or <tt>null</tt> if no blob is referenced
     */
    @Nullable
    public String getPath() {
        if (isEmpty() || getBlob() == null) {
            return null;
        }

        return getBlob().getPath();
    }

    /**
     * Returns the key of the referenced blob.
     *
     * @return the key of the blob being referenced
     */
    @Nullable
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getKey() {
        return key;
    }

    /**
     * Determines if a blob is being referenced.
     *
     * @return <tt>true</tt> if a reference is present, <tt>false</tt> otherwise
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public boolean isFilled() {
        return Strings.isFilled(key);
    }

    /**
     * Determines if no blob is being referenced.
     *
     * @return <tt>true</tt> if no reference is present, <tt>false</tt> otherwise
     */
    public boolean isEmpty() {
        return Strings.isEmpty(key);
    }

    /**
     * Determines if the referenced blob was fetched already.
     *
     * @return <tt>true</tt> if the blob is fetched, <tt>false</tt> otherwise
     */
    public boolean isFetched() {
        return key == null || blob != null;
    }

    @Override
    public String toString() {
        return "BlobHardRef: " + (isFilled() ? getKey() : "(empty)");
    }

    /**
     * Returns the space in which referenced blobs are stored.
     *
     * @return the name of the space in which referenced blobs are stored
     */
    public String getSpace() {
        return space;
    }

    /**
     * Provides a convenience method to resolve the {@link BlobStorageSpace} for <tt>space</tt>.
     * <p>
     * If possible, this uses the already resolved space from the blob being held.
     *
     * @return the storage space which holds the blobs referenced by this reference
     */
    public BlobStorageSpace getStorageSpace() {
        if (blob != null) {
            return blob.getStorageSpace();
        }

        return storage.getSpace(space);
    }

    /**
     * Provides a builder which can be used to create a delivery or download link.
     *
     * @return a builder to create a download or delivery URL
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public URLBuilder url() {
        if (blob != null) {
            return new URLBuilder(getStorageSpace(), blob);
        } else {
            return new URLBuilder(getStorageSpace(), key).withFallbackUri(fallbackUri);
        }
    }
}
