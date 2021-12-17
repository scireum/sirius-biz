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
import sirius.kernel.commons.Producer;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.ZipEntry;

/**
 * Represents a file extracted from a ZIP archive.
 */
public class ExtractedZipFile implements ExtractedFile {

    private final ZipEntry entry;
    private final Producer<InputStream> inputStreamSupplier;
    private final Amount progress;

    /**
     * Creates a new wrapped for the given entry stream and progress.
     *
     * @param entry               the entry being unzipped
     * @param inputStreamProducer a producer to obtain the stream
     * @param progress            a progress info if present
     */
    public ExtractedZipFile(ZipEntry entry, Producer<InputStream> inputStreamProducer, Amount progress) {
        this.entry = entry;
        this.inputStreamSupplier = inputStreamProducer;
        this.progress = progress;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        try {
            return CloseShieldInputStream.wrap(inputStreamSupplier.create());
        } catch (IOException ioException) {
            throw ioException;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .error(e)
                            .withSystemErrorMessage("Failed to unzip file from an archive: %s (%s)")
                            .handle();
        }
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
