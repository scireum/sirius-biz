/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.biz.storage.layer1.FileHandle;
import sirius.kernel.commons.Streams;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides a helper class which generates one or more ZIP archives for the given files.
 * <p>
 * Multiple files are generated if {@link #withMaxZipFileSize(long)} is used to specify an upper file size limit. The
 * supllier is provided which each generated file and must {@link FileHandle#close()} each received data so that
 * temporary data is released.
 * <p>
 * Also {@link #close()} has to be invoked to ensure that a valid and complete zip file is generated and sent
 * to the given consumer.
 */
public class ZipBuilder implements Closeable {

    private final Consumer<FileHandle> zipConsumer;
    private long maxZipFileSize = -1;

    private File currentZipFile;
    private ZipOutputStream zipOutputStream;

    /**
     * Creates a new builder which sends the created ZIP archives to the given consumer.
     *
     * @param zipConsumer the consumer of the generated archives. Note that {@link FileHandle#close()} has to be invoked
     *                    for each received file
     */
    public ZipBuilder(Consumer<FileHandle> zipConsumer) {
        this.zipConsumer = zipConsumer;
    }

    /**
     * Specifies an upper file size limit for the generated ZIP files.
     *
     * @param maxSizeInBytes the maximal size for each archive in bytes. Using 0 or negative numbers result in "no limit"
     *                       which is also the default.
     * @return the builder itself for fluent method calls
     */
    public ZipBuilder withMaxZipFileSize(long maxSizeInBytes) {
        this.maxZipFileSize = maxSizeInBytes;
        return this;
    }

    /**
     * Adds the given file to the ZIP archive.
     *
     * @param path      the path to use when creating the ZIP entry
     * @param fileToAdd the file to add to the archive
     * @throws IOException in case of an IO error while reading or writing data. Note that once an IO error ocurrend,
     *                     all subsequently emitted ZIP archives might be broken and should not be used. Still,
     *                     {@link FileHandle#close()} and {@link #close()} must be invoked to release all temporary
     *                     data.
     */
    public void addFile(String path, File fileToAdd) throws IOException {
        enforceZipFileSizeLimit(fileToAdd);
        ensureZipFileExists();

        zipOutputStream.putNextEntry(new ZipEntry(path));
        try (InputStream in = new FileInputStream(fileToAdd)) {
            Streams.transfer(in, zipOutputStream);
        }
        zipOutputStream.closeEntry();
        zipOutputStream.flush();
    }

    private void enforceZipFileSizeLimit(File fileToAdd) throws IOException {
        if (currentZipFile == null) {
            return;
        }

        if (maxZipFileSize <= 0) {
            return;
        }

        long remainingSize = maxZipFileSize - currentZipFile.length();
        if (remainingSize < fileToAdd.length()) {
            zipOutputStream.close();
            zipConsumer.accept(FileHandle.temporaryFileHandle(currentZipFile));
            currentZipFile = null;
        }
    }

    private void ensureZipFileExists() throws IOException {
        if (currentZipFile == null) {
            currentZipFile = Files.createTempFile("ZipBuilder", ".zip").toFile();
            zipOutputStream = new ZipOutputStream(new FileOutputStream(currentZipFile));
        }
    }


    /**
     * Provides a boilerplate method to directly add a <tt>FileHandle</tt> to the ZIP archive.
     * @param path      the path to use when creating the ZIP entry
     * @param fileToAdd the file to add. Note that {@link FileHandle#close()} will not be invoked for the given
     *                  handle.
     * @throws IOException in case of an IO error while reading or writing data. Note that once an IO error ocurrend,
     *                     all subsequently emitted ZIP archives might be broken and should not be used. Still,
     *                     {@link FileHandle#close()} and {@link #close()} must be invoked to release all temporary
     *                     data.
     */
    public void addFile(String path, FileHandle fileToAdd) throws IOException {
        addFile(path, fileToAdd.getFile());
    }

    @Override
    public void close() throws IOException {
        if (currentZipFile != null) {
            zipOutputStream.close();
            zipConsumer.accept(FileHandle.temporaryFileHandle(currentZipFile));
            currentZipFile = null;
        }
    }
}
