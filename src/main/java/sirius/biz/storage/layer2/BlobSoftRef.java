/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

import javax.annotation.Nullable;

/**
 * Represents a reference to a {@link Blob} which can be placed as field within an {@link BaseEntity}.
 * <p>
 * Being a soft reference the lifetime of the blob is <b>not</b> bound to the containing entity. Rather the blob
 * is selected from the browsable blobs of a space and continues to live independently if the referencing entity is
 * deleted.
 */
public class BlobSoftRef extends BlobHardRef {

    private final boolean supportsURL;
    private final BaseEntityRef.OnDelete deleteHandler;

    /**
     * Creates a new reference for the given space.
     *
     * @param space         the space to place referenced objects in
     * @param deleteHandler determines the action to take if the blob is deleted. Valid options are:
     *                      {@link BaseEntityRef.OnDelete#CASCADE CASCADE},
     *                      {@link BaseEntityRef.OnDelete#SET_NULL SET_NULL},
     *                      {@link BaseEntityRef.OnDelete#IGNORE IGNORE})
     * @param supportsURL   if <tt>true</tt> a URL can also be used instead of an object key
     */
    public BlobSoftRef(String space, BaseEntityRef.OnDelete deleteHandler, boolean supportsURL) {
        super(space);
        if (BaseEntityRef.OnDelete.REJECT == deleteHandler) {
            throw new IllegalArgumentException("BlobSoftRef references do not accept REJECT as deleteHandler.");
        }
        this.deleteHandler = deleteHandler;
        this.supportsURL = supportsURL;
    }

    /**
     * Creates a new reference for the given space.
     *
     * @param space         the space to place referenced objects in
     * @param deleteHandler determines what happens if the referenced entity is deleted
     */
    public BlobSoftRef(String space, BaseEntityRef.OnDelete deleteHandler) {
        this(space, deleteHandler, false);
    }

    @Override
    public BlobSoftRef withFallbackUri(String fallbackUri) {
        super.withFallbackUri(fallbackUri);
        return this;
    }

    @Override
    @Nullable
    public String getFilename() {
        if (isURL()) {
            return Strings.splitAtLast(getKey(), "/").getSecond();
        }

        return super.getFilename();
    }

    @Override
    @Nullable
    public String getPath() {
        if (isURL()) {
            return null;
        }

        return super.getPath();
    }

    /**
     * Determines if a URL was stored instead of an object key.
     *
     * @return <tt>true</tt> if a URL was stored, <tt>false</tt> if an object key or nothing yet is stored
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public boolean isURL() {
        return supportsURL && Urls.isHttpUrl(key);
    }

    @Override
    public String toString() {
        return "BlobSoftRef: " + (isFilled() ? getKey() : "(empty)");
    }

    /**
     * Determines if this reference also supports to store URLs instead of object keys.
     *
     * @return <tt>true</tt> if URLs can be stored in this reference, <tt>false</tt> otherwise
     */
    public boolean isSupportsURL() {
        return supportsURL;
    }

    @Override
    public URLBuilder url() {
        if (isURL()) {
            return new ExternalURLBuilder(key);
        }
        return super.url();
    }

    public BaseEntityRef.OnDelete getDeleteHandler() {
        return deleteHandler;
    }
}
