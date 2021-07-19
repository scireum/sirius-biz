/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.kernel.commons.Files;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Provides access to a stored object which has been materialized as file.
 * <p>
 * This is a {@link Closeable} as some storage frameworks need to perform some cleanups (i.e. delete the temporary file)
 * once the handle has been closed.
 */
public class FileHandle implements Closeable {

    private final File file;
    private final boolean deleteOnClose;

    protected FileHandle(File file, boolean deleteOnClose) {
        this.file = file;
        this.deleteOnClose = deleteOnClose;
    }

    /**
     * Creates a handle for a static file (which must not be deleted on close).
     *
     * @param file the file to wrap
     * @return a new handle wrapping the given file
     */
    public static FileHandle permanentFileHandle(File file) {
        return new FileHandle(file, false);
    }

    /**
     * Creates a handle for a temporary file (which must be deleted on close).
     *
     * @param file the file to wrap
     * @return a new handle wrapping the given temporary file
     */
    public static FileHandle temporaryFileHandle(File file) {
        return new FileHandle(file, true);
    }

    /**
     * Determines if the underlying file exists and isn't empty.
     *
     * @return <tt>true</tt> if the wrapped file exists and isn't empty
     */
    public boolean exists() {
        return file.exists() && file.length() > 0;
    }

    /**
     * Provides access to the underlying file.
     * <p>
     * This has to be handled with absolute care as one might not know if the file is a temporary copy or if
     * it represents the permanently stored file. Therefore modifying this file in any way is strictly prohibited.
     *
     * @return the underlying file
     */
    public File getFile() {
        return file;
    }

    /**
     * Provides read access to the wrapped file.
     * <p>
     * Note that this stream must not be accessed after {@link #close()} hase been called.
     *
     * @return an stream reading from the file
     * @throws FileNotFoundException in case of a non-existing file
     */
    public InputStream getInputStream() throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @Override
    public void close() {
        if (deleteOnClose) {
            Files.delete(file);
        }
    }
}
