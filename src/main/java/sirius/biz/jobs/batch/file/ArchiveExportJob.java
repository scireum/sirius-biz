/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import org.apache.commons.io.output.CloseShieldOutputStream;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides an export job which writes multiple files into a single archive file.
 */
public abstract class ArchiveExportJob extends FileExportJob {

    private ZipOutputStream zipOutputStream;

    /**
     * Creates a new job which writes into the given destination.
     *
     * @param destinationParameter the parameter used to select the destination for the file being written
     * @param process              the context in which the process will be executed
     */
    protected ArchiveExportJob(FileOrDirectoryParameter destinationParameter, ProcessContext process) {
        super(destinationParameter, process);
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

        return new CloseShieldOutputStream(zipOutputStream);
    }

    @Override
    protected final String determineFileExtension() {
        return "zip";
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
        protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
            destinationParameter.withAcceptedExtensions("zip");
            super.collectParameters(parameterCollector);
        }
    }
}

