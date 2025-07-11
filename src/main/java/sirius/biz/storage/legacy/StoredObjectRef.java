/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Part;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Represents a reference to a {@link StoredObject} which can be placed as field within an {@link BaseEntity}.
 * <p>
 * If the owning entity is deleted, the referenced object is also deleted if it was uploaded specifically
 * for this reference. If it was a shared object from within a bucket, the referenced object remains untouched.
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
public class StoredObjectRef {

    private final String bucket;
    private final boolean supportsURL;

    private StoredObject object;
    private String key;
    protected boolean changed;
    protected String reference;

    @Part
    private static Storage storage;

    /**
     * Creates a new reference for the given bucket and owner.
     *
     * @param bucket      the bucket to place referenced objects in
     * @param supportsURL if <tt>true</tt> a URL can also be used instead of an object key
     */
    public StoredObjectRef(String bucket, boolean supportsURL) {
        this.bucket = bucket;
        this.supportsURL = supportsURL;
    }

    /**
     * Creates a new reference for the given bucket and owner.
     *
     * @param bucket the bucket to place referenced objects in
     */
    public StoredObjectRef(String bucket) {
        this(bucket, false);
    }

    /**
     * Returns the referenced object or <tt>null</tt>.
     *
     * @return the referenced object or <tt>null</tt> if there is no referenced object
     */
    public StoredObject getObject() {
        if (object == null && Strings.isFilled(key)) {
            object = storage.findByKey(null, bucket, key).orElse(null);
            if (object == null) {
                key = null;
            }
        }
        return object;
    }

    /**
     * Assigns an object to be referenced.
     *
     * @param object the object to be referenced
     */
    public void setObject(StoredObject object) {
        this.object = object;
        if (object == null) {
            if (Strings.isFilled(this.key)) {
                this.changed = true;
            }
            this.key = null;
        } else {
            if (!Strings.areEqual(this.key, object.getObjectKey())) {
                this.changed = true;
            }
            this.key = object.getObjectKey();
        }
    }

    /**
     * Determines the URL for this reference. Returns either the URL if the reference is an external one, or the URL
     * for the {@link StoredObject} if it exists, otherwise the default URL.
     *
     * @param defaultURL the default URL
     * @return the URL for this reference
     */
    public String getURL(String defaultURL) {
        return getURLWithVersion(null, defaultURL);
    }

    /**
     * Determines the URL for this reference. Returns either the URL if the reference is an external one, or the
     * versioned URL for the {@link StoredObject} if it exists, otherwise the default URL.
     *
     * @param version    the version string
     * @param defaultURL the default URL
     * @return the URL for this reference
     */
    public String getURLWithVersion(String version, String defaultURL) {
        return getURLWithVersion(version, defaultURL, false);
    }

    /**
     * Determines the URL for this reference. Returns either the URL if the reference is an external one, or the
     * versioned URL for the {@link StoredObject} if it exists, otherwise the default URL.
     *
     * @param version        the version string
     * @param defaultURL     the default URL
     * @param eternallyValid whether a eternally valid url should be generated
     * @return the URL for this reference
     */
    public String getURLWithVersion(String version, String defaultURL, boolean eternallyValid) {
        return getURLWithVersionAndAddonText(version, null, defaultURL, eternallyValid);
    }

    /**
     * Determines the URL for this reference. Returns either the URL if the reference is an external one, or the
     * versioned URL for the {@link StoredObject} if it exists, otherwise the default URL.
     *
     * @param version    the version string
     * @param addonText  the addon text to add to the generated URL
     * @param defaultURL the default URL
     * @return the URL for this reference
     */
    public String getURLWithVersionAndAddonText(String version, String addonText, String defaultURL) {
        return getURLWithVersionAndAddonText(version, addonText, defaultURL, false);
    }

    /**
     * Determines the URL for this reference. Returns either the URL if the reference is an external one, or the
     * versioned URL for the {@link StoredObject} if it exists, otherwise the default URL.
     *
     * @param version        the version string
     * @param addonText      the addon text to add to the generated URL
     * @param defaultURL     the default URL
     * @param eternallyValid whether a eternally valid url should be generated
     * @return the URL for this reference
     */
    public String getURLWithVersionAndAddonText(String version,
                                                String addonText,
                                                String defaultURL,
                                                boolean eternallyValid) {
        if (isURL()) {
            return getKey();
        }

        if (isEmpty() || getObject() == null) {
            return defaultURL;
        }

        DownloadBuilder downloadBuilder = getObject().prepareURL().withVersion(version).withAddonText(addonText);

        if (eternallyValid) {
            downloadBuilder.eternallyValid();
        }

        return downloadBuilder.buildURL().orElse(defaultURL);
    }

    /**
     * Specifies an object key to reference.
     *
     * @param key the key of the object to reference
     */
    public void setKey(String key) {
        if (!Strings.areEqual(this.key, key)) {
            this.changed = true;
        }

        if (Strings.isEmpty(key)) {
            this.key = null;
            this.object = null;
        } else {
            this.key = key;
            if (this.object != null && !Strings.areEqual(this.object.getObjectKey(), key)) {
                this.object = null;
            }
        }
    }

    /**
     * Determines the filename of the referenced file or url.
     *
     * @return the filename, or <tt>null</tt> if no object is referenced
     */
    public String getFilename() {
        if (isURL()) {
            return Strings.splitAtLast(getKey(), "/").getSecond();
        }

        if (isEmpty() || getObject() == null) {
            return null;
        }

        return getObject().getFilename();
    }

    /**
     * Returns the key of the referenced object.
     *
     * @return the key of the object being referenced
     */
    public String getKey() {
        return key;
    }

    /**
     * Determines if an URL was stored instead of an object key.
     *
     * @return <tt>true</tt> if an URL was stored, <tt>false</tt> if an object key or nothing yet is stored
     */
    public boolean isURL() {
        return supportsURL && Urls.isHttpUrl(key);
    }

    /**
     * Determines if an object or an URL is being referenced.
     *
     * @return <tt>true</tt> if a reference or an URL is present, <tt>false</tt> otherwise
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public boolean isFilled() {
        return Strings.isFilled(key);
    }

    /**
     * Determines if no object or an URL is being referenced.
     *
     * @return <tt>true</tt> if no reference or URL is present, <tt>false</tt> otherwise
     */
    public boolean isEmpty() {
        return Strings.isEmpty(key);
    }

    /**
     * Determines if the referenced object was fetched already.
     *
     * @return <tt>true</tt> if the object is fetched, false otherwise
     */
    public boolean isFetched() {
        return key == null || object != null;
    }

    @Override
    public String toString() {
        return "StoredObjectRef: " + (isFilled() ? getKey() : "(empty)");
    }

    /**
     * Returns the bucket in which referenced objects are stored.
     *
     * @return the name of the bucket in which referenced objects are stored
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * Determines if this reference also supports to store URLs instead of object keys.
     *
     * @return <tt>true</tt> if URLs can be stored in this reference, <tt>false</tt> otherwise
     */
    public boolean isSupportsURL() {
        return supportsURL;
    }

    /**
     * Returns the effective reference used for {@link VirtualObject#REFERENCE}.
     *
     * @return the reference to use or an empty string of the owning object has not been saved yet.
     */
    public String getReference() {
        return reference;
    }
}
