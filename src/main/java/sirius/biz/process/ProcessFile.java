/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;

public class ProcessFile extends Nested {

    private String filename;

    private String fileId;

    private long size;

    public ProcessFile withFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public ProcessFile withFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }

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
