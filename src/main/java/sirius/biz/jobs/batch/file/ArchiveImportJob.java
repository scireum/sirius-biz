/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.util.ExtractedFile;
import sirius.biz.util.ExtractedZipFile;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides an import job which allows to import data from multiple archived files in a specific order.
 */
public abstract class ArchiveImportJob extends FileImportJob {

    private static final String ZIP_FILE_EXTENSION = "zip";

    /**
     * Contains a customized parameter which should be used in {@link FileImportJobFactory#createFileParameter()}
     * in its factory.
     */
    public static final Parameter<VirtualFile> ZIP_FILE_PARAMETER =
            FileImportJob.createFileParameter(Collections.singletonList(ZIP_FILE_EXTENSION),
                                              "$ArchiveImportJob.file.help");
    /**
     * References the imported ZIP file.
     * <p>
     * Note: Be cautious when accessing or altering this field directly.
     */
    protected ZipFile zipFile;

    /**
     * Creates a new job for the given process context.
     *
     * @param process the process context in which the job is executed
     */
    protected ArchiveImportJob(ProcessContext process) {
        super(process);
    }

    @Override
    public void execute() throws Exception {
        VirtualFile file = process.require(FileImportJob.FILE_PARAMETER);
        auxiliaryFileMode = process.getParameter(AUX_FILE_MODE_PARAMETER).orElse(AuxiliaryFileMode.IGNORE);
        flattenAuxiliaryFileDirs = process.getParameter(AUX_FILE_FLATTEN_DIRS_PARAMETER).orElse(false);

        if (canHandleFileExtension(file.fileExtension())) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.downloadingFile")
                                  .withContext("file", file.name())
                                  .withContext("size", NLS.formatSize(file.size())));
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
     * @throws Exception in case of an exception during importing
     */
    protected abstract void importEntries() throws Exception;

    /**
     * Fetches an entry from the archive and returns it's input stream.
     * <p>
     * Note, that previously opened input stream might get closed by performing this action.
     *
     * @param fileName the name of the file to fetch
     * @return the extracted file, or an empty optional if the file wasn't found
     * @throws IOException in case of an IO error while extracting the file
     */
    protected Optional<ExtractedFile> fetchEntry(String fileName) throws IOException {
        TaskContext taskContext = TaskContext.get();
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements() && taskContext.isActive()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            if (Files.isConsideredHidden(zipEntry.getName()) || Files.isConsideredMetadata(zipEntry.getName())) {
                continue;
            }
            if (Strings.areEqual(fileName, zipEntry.getName())) {
                return Optional.of(new ExtractedZipFile(zipEntry,
                                                        () -> zipFile.getInputStream(zipEntry),
                                                        Amount.NOTHING));
            }
        }

        return Optional.empty();
    }

    protected void handleMissingFile(String fileName, boolean isRequired) {
        if (isRequired) {
            throw createMissingFileException(fileName);
        } else {
            process.log(ProcessLog.info()
                                  .withNLSKey("ArchiveImportJob.errorMsg.optionalFileMissing")
                                  .withContext("fileName", fileName)
                                  .withMessageType("$ArchiveImportJob.errorMsg.optionalFileMissing.messageType"));
        }
    }

    protected HandledException createMissingFileException(String fileName) {
        return Exceptions.createHandled()
                         .withNLSKey("ArchiveImportJob.errorMsg.requiredFileMissing")
                         .set("fileName", fileName)
                         .handle();
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

    /**
     * Streams all entries of the archive.
     * <p>
     * Technically, this method just wraps the method {@link #extractAllFiles(Consumer)} into a {@link Stream}.
     *
     * @return a stream of all entries in the archive
     */
    protected Stream<ExtractedZipFile> streamEntries() {
        Stream.Builder<ExtractedZipFile> builder = Stream.builder();
        extractAllFiles(builder);
        return builder.build();
    }

    /**
     * Extracts all files from the archive
     *
     * @param fileHandler a handler to be invoked for each file in the archive
     */
    protected void extractAllFiles(Consumer<ExtractedZipFile> fileHandler) {
        TaskContext taskContext = TaskContext.get();
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements() && taskContext.isActive()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            if (Files.isConsideredHidden(zipEntry.getName()) || Files.isConsideredMetadata(zipEntry.getName())) {
                continue;
            }
            fileHandler.accept(new ExtractedZipFile(zipEntry, () -> zipFile.getInputStream(zipEntry), Amount.NOTHING));
        }
    }

    @Override
    protected final boolean canHandleFileExtension(@Nullable String fileExtension) {
        return ZIP_FILE_EXTENSION.equalsIgnoreCase(fileExtension);
    }

    @Override
    protected void executeForStream(String filename, Producer<InputStream> in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
        super.close();
    }
}
