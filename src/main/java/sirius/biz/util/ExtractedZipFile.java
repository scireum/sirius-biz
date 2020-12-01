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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;

/**
 * Represents a file extracted from a ZIP archive.
 */
class ExtractedZipFile implements ExtractedFile {

    private final ZipEntry entry;
    private final Supplier<InputStream> inputStreamSupplier;
    private final Amount progress;

    ExtractedZipFile(ZipEntry entry, Supplier<InputStream> inputStreamSupplier, Amount progress) {
        this.entry = entry;
        this.inputStreamSupplier = inputStreamSupplier;
        this.progress = progress;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new CloseShieldInputStream(inputStreamSupplier.get());
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
