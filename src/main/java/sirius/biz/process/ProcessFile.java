/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;

import java.io.File;

/**
 * Represents a file which as been stored for a process.
 * <p>
 * Use {@link ProcessContext#addFile(String, File)} to directly create and persist a file for a process.
 */
public class ProcessFile extends Nested {

    /**
     * Contains the filename of the file.
     */
    private String filename;

    /**
     * Contains a unique ID used by the {@link ProcessFileStorage} to identify this file.
     */
    private String fileId;

    /**
     * Contains the size in bytes.
     */
    private long size;

    /**
     * Specifies the filename.
     *
     * @param filename the filename of the file
     * @return the file itself for fluent method calls
     */
    public ProcessFile withFilename(String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * Specifies the id as provided by the {@link ProcessFileStorage}.
     *
     * @param fileId the id used to store the data
     * @return the file itself for fluent method calls
     */
    public ProcessFile withFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }

    /**
     * Specifies the size in bytes.
     *
     * @param size the size in bytes
     * @return the file itself for fluent method calls
     */
    public ProcessFile withSize(long size) {
        this.size = size;
        return this;
    }

    public String getFilename() {
        return filename;
    }

    public String getFileId() {
        return fileId;
    }

    public long getSize() {
        return size;
    }
}
