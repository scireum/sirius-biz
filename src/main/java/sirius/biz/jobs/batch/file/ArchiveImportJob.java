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
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides an import job which allows to import data from multiple archived files in a specific order.
 */
public abstract class ArchiveImportJob extends FileImportJob {

    /**
     * Contains the file name and input stream of all archive entries whose extensions can be handled.
     */
    private final Map<String, InputStream> entries = new HashMap<>();

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

        if (canHandleFileExtension(Value.of(file.fileExtension()).toLowerCase())) {
            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                executeForSingleFile(fileHandle);
            }
        } else if (FILE_EXTENSION_ZIP.equalsIgnoreCase(file.fileExtension())) {
            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                executeForArchive(fileHandle);
            }
        } else {
            throw Exceptions.createHandled().withNLSKey("FileImportJob.fileNotSupported").handle();
        }
    }

    private void executeForSingleFile(FileHandle fileHandle) throws Exception {
        entries.put(fileHandle.getFile().getName(), fileHandle.getInputStream());
        importFiles();
    }

    private void executeForArchive(FileHandle fileHandle) throws Exception {
        process.log(ProcessLog.info().withNLSKey("FileImportJob.importingZipFile"));

        try (ZipFile zipFile = new ZipFile(fileHandle.getFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

            int validArchiveEntries = 0;

            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();

                if (!isHiddenFile(zipEntry.getName())
                    && canHandleFileExtension(Files.getFileExtension(zipEntry.getName()))) {
                    this.entries.put(zipEntry.getName(), zipFile.getInputStream(zipEntry));
                    validArchiveEntries++;
                }
            }

            if (validArchiveEntries == 0) {
                throw Exceptions.createHandled().withNLSKey("FileImportJob.noZippedFileFound").handle();
            }

            importFiles();
        }
    }

    /**
     * Imports data based on the open input streams.
     *
     * @throws Exception in case of an exception while importing
     */
    protected abstract void importFiles() throws Exception;

    protected List<InputStream> getFiles() {
        return new ArrayList<>(entries.values());
    }

    @Nullable
    protected InputStream getFile(String fileName, boolean isRequired) {
        InputStream inputStream = entries.get(fileName);

        if (inputStream == null) {
            handleMissingFile(fileName, isRequired);
        }

        return inputStream;
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
        return Arrays.stream(fileNamesToCheck).allMatch(entries::containsKey);
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        // never called in overwritten execute method
    }

    @Override
    public void close() throws IOException {
        closeInputStreams();
        super.close();
    }

    private void closeInputStreams() {
        entries.values().forEach(file -> {
            try {
                file.close();
            } catch (IOException e) {
                process.handle(e);
            }
        });
    }
}
