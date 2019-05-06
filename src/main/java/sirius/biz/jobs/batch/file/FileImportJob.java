/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ImportJob;
import sirius.biz.jobs.params.VirtualObjectParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.Storage;
import sirius.biz.storage.VirtualObject;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provides an import job which reads and imports a file.
 */
public abstract class FileImportJob extends ImportJob {

    @Part
    private static Storage storage;

    private FileImportJobFactory factory;
    private VirtualObjectParameter fileParameter;

    /**
     * Creates a new job for the given factory and process context.
     *
     * @param fileParameter the parameter which is used to derive the import file from
     * @param process       the process context in which the job is executed
     */
    protected FileImportJob(VirtualObjectParameter fileParameter, ProcessContext process) {
        super(process);
        this.fileParameter = fileParameter;
    }

    @Override
    public void execute() throws Exception {
        VirtualObject file = process.require(fileParameter);

        try (InputStream in = storage.getData(file)) {
            if (!canHandleFileExtension(file.getFileExtension()) && "zip".equalsIgnoreCase(file.getFileExtension())) {
                executeForZippedFile(in);

                return;
            }

            if (!canHandleFileExtension(file.getFileExtension())) {
                throw Exceptions.createHandled().withNLSKey("FileImportJob.fileNotSupported").handle();
            }

            executeForStream(file.getFilename(), in);
        }
    }

    /**
     * Performs the import for a zipped file.
     *
     * @param in the data to import
     * @throws Exception in case of an error during the import
     */
    protected void executeForZippedFile(InputStream in) throws Exception {
        process.log(ProcessLog.info().withNLSKey("FileImportJob.importingZipFile"));

        try (ZipInputStream zipInputStream = new ZipInputStream(in)) {
            ZipEntry entry = zipInputStream.getNextEntry();

            while (entry != null) {
                if (!isHiddenFile(entry.getName()) && canHandleFileExtension(Files.getFileExtension(entry.getName()))) {
                    process.log(ProcessLog.info()
                                          .withNLSKey("FileImportJob.importingZippedFile")
                                          .withContext("filename", entry.getName()));

                    executeForStream(entry.getName(), zipInputStream);

                    return;
                }

                entry = zipInputStream.getNextEntry();
            }

            throw Exceptions.createHandled().withNLSKey("FileImportJob.noZippedFileFound").handle();
        }
    }

    private boolean isHiddenFile(String name) {
        String fileName = Files.getFilenameAndExtension(name);

        if (Strings.isEmpty(fileName)) {
            return false;
        }

        return fileName.startsWith(".");
    }

    /**
     * Actually performs the import for the given input stream.
     *
     * @param filename the name of the file being imported
     * @param in       the data to import
     * @throws Exception in case of an error during the import
     */
    protected abstract void executeForStream(String filename, InputStream in) throws Exception;

    /**
     * Determines if the given file extension can be handled by the import job.
     *
     * @param fileExtension the file extension to check
     * @return <tt>true</tt> if it can be handled, <tt>false</tt> otherwise
     */
    protected abstract boolean canHandleFileExtension(String fileExtension);
}
