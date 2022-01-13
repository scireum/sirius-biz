/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.VirtualFile;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Provides an export job which writes multiple files into a single archive file.
 */
public abstract class ArchiveExportJob extends FileExportJob {

    private static final String ZIP_FILE_EXTENSION = "zip";
    private ZipOutputStream zipOutputStream;

    /**
     * Creates a new job which writes into the given destination.
     *
     * @param process the context in which the process will be executed
     */
    protected ArchiveExportJob(ProcessContext process) {
        super(process);
    }

    /**
     * Creates a new entry in the archive and returns its output stream.
     *
     * @param fileName the name of the archived file
     * @return a new output stream that points to the created entry
     * @throws IOException in case the output stream couln't be created
     */
    @Nonnull
    protected OutputStream createEntry(String fileName) throws IOException {
        if (zipOutputStream == null) {
            zipOutputStream = new ZipOutputStream(createOutputStream());
        }

        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOutputStream.putNextEntry(zipEntry);

        return CloseShieldOutputStream.wrap(zipOutputStream);
    }

    @Override
    protected final String determineFileExtension() {
        return ZIP_FILE_EXTENSION;
    }

    @Override
    public void close() throws IOException {
        if (zipOutputStream != null) {
            zipOutputStream.close();
        }
        super.close();
    }

    /**
     * Provides a simple factory for archive based export jobs.
     */
    public abstract static class ArchiveExportJobFactory extends FileExportJobFactory {
        @Override
        protected Parameter<VirtualFile> getDestinationParameter() {
            return FileExportJob.createDestinationParameter(Collections.singletonList(ZIP_FILE_EXTENSION));
        }
    }

    /**
     * Digests every entry of a fresh created export archive.
     * <p>
     * Use this method in order to perform validations on contents of the final archive.
     *
     * @param digester a consumer receiving the name and the {@link InputStream} of each entry
     */
    protected void digestExportedFile(BiConsumer<String, InputStream> digester) {
        digestExportedFile(inputStream -> processExportedArchive(inputStream, digester));
    }

    private void processExportedArchive(InputStream inputStream, BiConsumer<String, InputStream> digester) {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                digester.accept(entry.getName(), CloseShieldInputStream.wrap(zipInputStream));
                entry = zipInputStream.getNextEntry();
            }
        } catch (IOException e) {
            process.handle(e);
        }
    }
}

