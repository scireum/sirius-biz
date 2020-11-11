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
 * Represents a file which has been extraced from an archive by the {@link ArchiveExtractor}.
 */
public interface ExtractedFile {

    /**
     * Creates an input stream to actually read the file.
     *
     * @return an input stream reading from the extracted file
     * @throws IOException in case of an IO error
     */
    InputStream openInputStream() throws IOException;

    /**
     * Returns the uncompressed size of the file.
     *
     * @return the uncompressed size of the extracted file
     */
    long size();

    /**
     * Returns the last modification timestamp of the file.
     *
     * @return the timestamp when the file was last modified
     */
    LocalDateTime lastModified();

    /**
     * Returns the path of the file.
     *
     * @return the path as indicated by the archive
     */
    String getFilePath();

    /**
     * Returns the progress in percent based on the number of files processed.
     *
     * @return the ratio between the number of processed files and the total number of files in the archive in percent
     * (ranging from 0 to 100).
     */
    Amount getProgressInPercent();
}
