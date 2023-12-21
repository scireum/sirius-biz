/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import org.apache.commons.io.output.DeferredFileOutputStream;
import sirius.kernel.commons.Files;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps a {@link DeferredFileOutputStream stream} that contains an extracted file either retained in memory or in
 * a temporary file.
 */
class ExtractedFileBuffer {

    private static final int DEFAULT_IN_MEMORY_THRESHOLD = 1024 * 1024 * 4;
    private static final int DEFAULT_INITIAL_BUFFER_SIZE = 1024 * 128;
    private static final String DEFAULT_TMP_FILE_PREFIX = "sirius_archive_";

    private final DeferredFileOutputStream buffer;

    /**
     * Creates a buffer with the default settings.
     */
    ExtractedFileBuffer() {
        this(DEFAULT_IN_MEMORY_THRESHOLD, DEFAULT_INITIAL_BUFFER_SIZE, DEFAULT_TMP_FILE_PREFIX);
    }

    /**
     * Creates a buffer with the specified settings.
     *
     * @param memoryThreshold   the threshold before the data is written to a temporary file in bytes
     * @param initialBufferSize the initial size of the underlying output stream in bytes
     * @param tempFilePrefix    the prefix of the temporary file which will be created if the memory threshold is reached
     */
    ExtractedFileBuffer(int memoryThreshold, int initialBufferSize, String tempFilePrefix) {
        DeferredFileOutputStream.Builder builder = new DeferredFileOutputStream.Builder();
        builder.setThreshold(memoryThreshold).setBufferSize(initialBufferSize).setPrefix(tempFilePrefix);
        buffer = builder.get();
    }

    /**
     * Adds the given bytes to the buffer which may write the contents into a file.
     *
     * @param bytes the bytes to add to the buffer
     * @throws IOException in case the bytes couldn't be written to the stream or file.
     */
    public void write(byte[] bytes) throws IOException {
        buffer.write(bytes);
    }

    /**
     * Returns a new input stream from either the in memory kept byte array or the temporary file.
     * <p>
     * Please note that the caller is responsible for closing the stream afterwards.
     *
     * @return a new input stream from either the in memory kept byte array or the temporary file
     */
    public InputStream getInputStream() {
        if (buffer.isInMemory()) {
            return new ByteArrayInputStream(buffer.getData());
        }
        try {
            return new FileInputStream(buffer.getFile());
        } catch (FileNotFoundException e) {
            throw Exceptions.createHandled().withSystemErrorMessage("No file found inside buffer").handle();
        }
    }

    /**
     * Returns the number of bytes that are saved in memory or in a temporary file.
     *
     * @return the number of bytes that are saved in memory or in a temporary file
     */
    public long getSize() {
        if (buffer.isInMemory()) {
            return buffer.getData().length;
        } else {
            return buffer.getFile().length();
        }
    }

    /**
     * Releases all internally acquired resources.
     */
    public void cleanup() {
        try {
            buffer.close();
            if (!buffer.isInMemory()) {
                Files.delete(buffer.getFile());
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(Log.SYSTEM)
                      .error(e)
                      .withSystemErrorMessage(
                              "Failed to close a temporary buffer created when extracting an archive: %s (%s)");
        }
    }
}
