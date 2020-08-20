/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides an import job which allows to import data from multiple archived files in a specific order.
 */
public abstract class ArchiveImportJob extends FileImportJob {

    private ZipFile zipFile;

    /**
     * Creates a new job for the given process context.
     *
     * @param fileParameter the parameter which is used to derive the import file from
     * @param process       the process context in which the job is executed
     */
    protected ArchiveImportJob(FileParameter fileParameter, ProcessContext process) {
        super(fileParameter, process);
    }

    @Override
    public void execute() throws Exception {
        VirtualFile file = process.require(fileParameter);

        if (canHandleFileExtension(file.fileExtension())) {
            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                zipFile = new ZipFile(fileHandle.getFile());
                importEntries();
            }
        } else {
            throw Exceptions.createHandled().withNLSKey("FileImportJob.fileNotSupported").handle();
        }
    }

    /**
     * Imports data based on files inside the 'opened' archive.
     *
     * @throws Exception in case of a exception during importing
     */
    protected abstract void importEntries() throws Exception;

    /**
     * Fetches an entry from the archive and returns it's input stream.
     * <p>
     * Note, that previously opened input stream might get closed by performing this action.
     *
     * @param fileName   the name of the file to fetch
     * @param isRequired flag if the file is required
     * @return input stream for the requested file
     * @throws Exception in case of an exception during fetching or if the file wasn't found but is required
     */
    @Nullable
    protected InputStream fetchEntry(String fileName, boolean isRequired) throws Exception {
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();

            if (Strings.areEqual(fileName, zipEntry.getName())) {
                return zipFile.getInputStream(zipEntry);
            }
        }

        handleMissingFile(fileName, isRequired);
        return null;
    }

    protected void handleMissingFile(String fileName, boolean isRequired) {
        if (isRequired) {
            throw Exceptions.createHandled()
                            .withNLSKey("ArchiveImportJob.errorMsg.requiredFileMissing")
                            .set("fileName", fileName)
                            .handle();
        } else {
            process.log(ProcessLog.warn()
                                  .withNLSKey("ArchiveImportJob.errorMsg.optionalFileMissing")
                                  .withContext("fileName", fileName)
                                  .withMessageType(fileName));
        }
    }

    /**
     * Checks if all given files are found in the archive.
     *
     * @param fileNamesToCheck all file names that should be inside the archive
     * @return <tt>true</tt> if the archive contains all the given files, <tt>false</tt> otherwise
     */
    protected boolean containsEntries(String... fileNamesToCheck) {
        ArrayList<? extends ZipEntry> zipEntries = Collections.list(zipFile.entries());

        return Arrays.stream(fileNamesToCheck)
                     .allMatch(fileName -> zipEntries.stream().anyMatch(entry -> entry.getName().equals(fileName)));
    }

    @Override
    protected final boolean canHandleFileExtension(@Nullable String fileExtension) {
        return FILE_EXTENSION_ZIP.equalsIgnoreCase(fileExtension);
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
        super.close();
    }
}
