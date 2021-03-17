/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.kernel.commons.Amount;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * Represents a file extracted by 7-ZIP.
 */
class Extracted7ZFile implements ExtractedFile {

    private final ExtractedFileBuffer data;
    private final String filePath;
    private final Amount progress;
    private final LocalDateTime lastModified;

    Extracted7ZFile(ExtractedFileBuffer data, String filePath, LocalDateTime lastModified, Amount progress) {
        this.data = data;
        this.filePath = filePath;
        this.progress = progress;
        this.lastModified = lastModified;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return data.getInputStream();
    }

    @Override
    public long size() {
        return data.getSize();
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public Amount getProgressInPercent() {
        return progress.toPercent();
    }

    @Override
    public LocalDateTime lastModified() {
        return lastModified;
    }
}
