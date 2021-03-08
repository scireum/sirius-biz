/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import java.time.LocalDateTime;

/**
 * Represents metadata about a stored object.
 */
public class ObjectMetadata {
    private String key;
    private LocalDateTime lastModified;
    private long size;

    /**
     * Creates new metadata for the given key.
     *
     * @param key          the object for which this metadata has been created
     * @param lastModified the last modification timestamp of the object
     * @param size         the size of the object
     */
    public ObjectMetadata(String key, LocalDateTime lastModified, long size) {
        this.key = key;
        this.lastModified = lastModified;
        this.size = size;
    }

    public String getKey() {
        return key;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public long getSize() {
        return size;
    }
}
