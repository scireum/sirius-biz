/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import org.apache.commons.io.input.CloseShieldInputStream;
import sirius.biz.jobs.batch.ImportJob;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provides an import job which reads and imports a file.
 */
public abstract class FileImportJob extends ImportJob {

    protected static final String FILE_EXTENSION_ZIP = "zip";

    protected FileParameter fileParameter;

    @Part
    private static VirtualFileSystem vfs;

    protected ValueHolder<VirtualFile> unusedFilesDestination;

    /**
     * Creates a new job for the given factory and process context.
     *
     * @param fileParameter the parameter which is used to derive the import file from
     * @param process       the process context in which the job is executed
     */
    protected FileImportJob(FileParameter fileParameter, ProcessContext process) {
        super(process);
        this.fileParameter = fileParameter;
    }

    @Override
    public void execute() throws Exception {
        VirtualFile file = process.require(fileParameter);

        if (canHandleFileExtension(Value.of(file.fileExtension()).toLowerCase())) {
            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                executeForSingleFile(file.name(), fileHandle);
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

    /**
     * Creates a backup of the file being imported by attaching it to the process.
     * <p>
     * This can be suppressed by overwriting this method.
     *
     * @param filename the name of the file to backup
     * @param input    the input file to backup
     */
    protected void backupInputFile(String filename, FileHandle input) {
        attachFile(filename, input);
    }

    protected void executeForSingleFile(String fileName, FileHandle fileHandle) throws Exception {
        try (InputStream in = fileHandle.getInputStream()) {
            executeForStream(fileName, in);
        }
    }

    protected void executeForArchive(FileHandle fileHandle) throws Exception {
        process.log(ProcessLog.info().withNLSKey("FileImportJob.importingZipFile"));

        try (ZipInputStream zipInputStream = new ZipInputStream(fileHandle.getInputStream())) {
            ZipEntry entry = zipInputStream.getNextEntry();

            int filesImported = 0;
            while (entry != null) {
                if (executeForEntry(zipInputStream, entry)) {
                    filesImported++;
                }

                entry = zipInputStream.getNextEntry();
            }

            if (filesImported == 0) {
                throw Exceptions.createHandled().withNLSKey("FileImportJob.noZippedFileFound").handle();
            }
        }
    }

    private boolean executeForEntry(ZipInputStream zipInputStream, ZipEntry entry) throws Exception {
        if (isHiddenFile(entry.getName())) {
            return false;
        }

        if (canHandleFileExtension(Files.getFileExtension(entry.getName()))) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.importingZippedFile")
                                  .withContext("filename", entry.getName()));

            executeForStream(entry.getName(), new CloseShieldInputStream(zipInputStream));
            return true;
        } else {
            return handleUnsupportedFile(entry.getName(),
                                         entry.getSize(),
                                         entry.getLastModifiedTime().toInstant(),
                                         new CloseShieldInputStream(zipInputStream));
        }
    }

    /**
     * Gets invoked for every entry in a given ZIP archive which cannot be processed by this job itself.
     * <p>
     * This might be used e.g. if an XML file is being processed which is accompanied with some media files to
     * move them into the proper direcotry in the {@link sirius.biz.storage.layer3.VirtualFileSystem}.
     * <p>
     * By default this is attempted if the {@link #determineAuxiliaryFilesDirectory()} returns a non-null result.
     * Otherwise these files are simply ignored.
     *
     * @param name    the name of the file
     * @param size    the (uncompressed) size in bytes
     * @param instant the last modificaion timestamp of the file
     * @param data    the contents of the file
     */
    protected boolean handleUnsupportedFile(String name, long size, Instant instant, InputStream data) {
        try {
            Watch watch = Watch.start();
            VirtualFile basePath = determineAuxiliaryFilesBasePath();
            if (basePath == null) {
                return false;
            }

            basePath.resolve(name).consumeStream(data, size);
            process.addTiming(NLS.get("FileImportJob.auxiliaryFilesCopied"), watch.elapsedMillis());
            return true;
        } catch (Exception e) {
            process.handle(Exceptions.handle()
                                     .error(e)
                                     .to(Log.BACKGROUND)
                                     .withNLSKey("FileImportJob.copyAuxiliaryFileFailed")
                                     .set("file", name)
                                     .handle());
            return false;
        }
    }

    @Nullable
    private VirtualFile determineAuxiliaryFilesBasePath() {
        if (unusedFilesDestination == null) {
            String unusedFilesPath = determineAuxiliaryFilesDirectory();
            if (Strings.isEmpty(unusedFilesPath)) {
                unusedFilesDestination = ValueHolder.of(null);
            } else {
                VirtualFile destination = vfs.resolve(unusedFilesPath);
                if (destination.exists() && destination.isDirectory()) {
                    unusedFilesDestination = ValueHolder.of(destination);
                } else {
                    unusedFilesDestination = ValueHolder.of(null);
                }
            }
        }

        return unusedFilesDestination.get();
    }

    /**
     * Determines where (in the {@link VirtualFileSystem}) to store auxiliary files.
     * <p>
     * These are files which reside in a ZIP archive next to the files which are actually being processed by this
     * job.
     *
     * @return the path where to store auxillary files or <tt>null</tt> to ignore them
     */
    protected String determineAuxiliaryFilesDirectory() {
        return null;
    }

    protected boolean isHiddenFile(String name) {
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
     * @param fileExtension the file extension to check (this is guaranteed to be lowercase).
     * @return <tt>true</tt> if it can be handled, <tt>false</tt> otherwise
     */
    protected abstract boolean canHandleFileExtension(@Nullable String fileExtension);
}
