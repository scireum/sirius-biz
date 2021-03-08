/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.nls.NLS;

import java.time.LocalDateTime;

/**
 * Provides some metadata for a file in the Jupiter repository.
 */
public class RepositoryFile {
    private final String name;
    private final long size;
    private final LocalDateTime lastModified;

    /**
     * Creates a new wrapper for the given data.
     *
     * @param name         the name of the file
     * @param size         the size in bytes
     * @param lastModified the last modified timestamp
     */
    public RepositoryFile(String name, long size, LocalDateTime lastModified) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
    }

    /**
     * Returns the name of the file.
     *
     * @return the name (or repository relative path)
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the size of the file.
     *
     * @return the size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the last modified date.
     *
     * @return the timestamp when the file was last modified / fetched from a server
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    @Override
    public String toString() {
        return name + "(" + NLS.formatSize(size) + ", " + NLS.toUserString(lastModified) + ")";
    }
}
