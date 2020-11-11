/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import org.apache.commons.io.input.CloseShieldInputStream;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.ZipEntry;

/**
 * Represents a file extracted from a ZIP archive.
 */
class ExtractedZipFile implements ExtractedFile {

    private final ZipEntry entry;
    private final InputStream zipInputStream;
    private final Amount progress;

    ExtractedZipFile(ZipEntry entry, InputStream zipInputStream, Amount progress) {
        this.entry = entry;
        this.zipInputStream = zipInputStream;
        this.progress = progress;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new CloseShieldInputStream(zipInputStream);
    }

    @Override
    public long size() {
        return entry.getSize();
    }

    @Override
    public LocalDateTime lastModified() {
        return LocalDateTime.ofInstant(entry.getLastModifiedTime().toInstant(), ZoneId.systemDefault());
    }

    @Override
    public String getFilePath() {
        return entry.getName();
    }

    @Override
    public Amount getProgressInPercent() {
        return progress.toPercent();
    }
}
