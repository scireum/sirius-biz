/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ImportJob;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.biz.util.ArchiveExtractor;
import sirius.biz.util.ExtractedFile;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Producer;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides an import job which reads and imports a file.
 */
public abstract class FileImportJob extends ImportJob {

    public static final Parameter<VirtualFile> FILE_PARAMETER = createFileParameter(null);

    public static final Parameter<AuxiliaryFileMode> AUX_FILE_MODE_PARAMETER = new EnumParameter<>("auxFileMode",
                                                                                                   "$FileImportJobFactory.auxFileMode",
                                                                                                   AuxiliaryFileMode.class)
            .withDefault(AuxiliaryFileMode.UPDATE_ON_CHANGE)
            .markRequired()
            .build();

    @Part
    private static VirtualFileSystem vfs;

    @Part
    private static ArchiveExtractor extractor;

    protected ValueHolder<VirtualFile> unusedFilesDestination;
    protected AuxiliaryFileMode auxiliaryFileMode;

    public enum AuxiliaryFileMode {
        IGNORE(ArchiveExtractor.OverrideMode.NEVER), ALWAYS_UPDATE(ArchiveExtractor.OverrideMode.ALWAYS),
        UPDATE_ON_CHANGE(ArchiveExtractor.OverrideMode.ON_CHANGE), NEVER_UPDATE(ArchiveExtractor.OverrideMode.NEVER);

        private final ArchiveExtractor.OverrideMode overrideMode;

        AuxiliaryFileMode(ArchiveExtractor.OverrideMode overrideMode) {
            this.overrideMode = overrideMode;
        }

        ArchiveExtractor.OverrideMode getOverrideMode() {
            return overrideMode;
        }

        @Override
        public String toString() {
            return NLS.get(getClass().getSimpleName() + "." + name());
        }
    }

    /**
     * Creates a new job for the given factory and process context.
     *
     * @param process the process context in which the job is executed
     */
    protected FileImportJob(ProcessContext process) {
        super(process);
    }

    public static Parameter<VirtualFile> createFileParameter(@Nullable List<String> acceptedFileExtensions) {
        FileParameter result = new FileParameter("file", "$FileImportJobFactory.file").withBasePath("/work");
        if (acceptedFileExtensions != null && !acceptedFileExtensions.isEmpty()) {
            result.withAcceptedExtensionsList(acceptedFileExtensions);
        }
        return result.markRequired().build();
    }

    @Override
    public void execute() throws Exception {
        VirtualFile file = process.require(FILE_PARAMETER);
        auxiliaryFileMode = process.getParameter(AUX_FILE_MODE_PARAMETER).orElse(AuxiliaryFileMode.IGNORE);

        if (canHandleFileExtension(Value.of(file.fileExtension()).toLowerCase())) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.downloadingFile")
                                  .withContext("file", file.name())
                                  .withContext("size", NLS.formatSize(file.size())));

            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                executeForStream(file.name(), () -> fileHandle.getInputStream());
            }
        } else if (extractor.isArchiveFile(file.fileExtension())) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.downloadingFile")
                                  .withContext("file", file.name())
                                  .withContext("size", NLS.formatSize(file.size())));

            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                executeForArchive(file.name(), fileHandle);
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

    protected void executeForArchive(String filename, FileHandle fileHandle) throws Exception {
        process.log(ProcessLog.info().withNLSKey("FileImportJob.importingZipFile"));

        AtomicInteger filesImported = new AtomicInteger();
        extractor.extractAll(filename, fileHandle.getFile(), null, file -> {
            if (executeForEntry(file)) {
                filesImported.incrementAndGet();
            }
        });

        if (filesImported.get() == 0) {
            throw Exceptions.createHandled().withNLSKey("FileImportJob.noZippedFileFound").handle();
        }
    }

    private boolean executeForEntry(ExtractedFile extractedFile) throws Exception {
        if (canHandleFileExtension(Files.getFileExtension(extractedFile.getFilePath()))) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.importingZippedFile")
                                  .withContext("filename", extractedFile.getFilePath()));
            executeForStream(extractedFile.getFilePath(), extractedFile::openInputStream);
            return true;
        } else if (auxiliaryFileMode != AuxiliaryFileMode.IGNORE) {
            return handleUnsupportedFile(extractedFile);
        } else {
            return false;
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
     * @param extractedFile the extracted file which cannot be handled by the job itself
     */
    protected boolean handleUnsupportedFile(ExtractedFile extractedFile) {
        try {
            Watch watch = Watch.start();
            VirtualFile basePath = determineAuxiliaryFilesBasePath();
            if (basePath == null) {
                return false;
            }

            VirtualFile targetFile = basePath.resolve(extractedFile.getFilePath());
            if (extractor.updateFile(extractedFile, targetFile, auxiliaryFileMode.overrideMode)
                != ArchiveExtractor.UpdateResult.SKIPPED) {
                process.addTiming(NLS.get("FileImportJob.auxiliaryFileCopied"), watch.elapsedMillis());
            } else {
                process.addTiming(NLS.get("FileImportJob.auxiliaryFileSkipped"), watch.elapsedMillis());
            }

            return true;
        } catch (Exception e) {
            process.handle(Exceptions.handle()
                                     .error(e)
                                     .to(Log.BACKGROUND)
                                     .withNLSKey("FileImportJob.copyAuxiliaryFileFailed")
                                     .set("file", extractedFile.getFilePath())
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

    /**
     * Actually performs the import for the given input stream.
     *
     * @param filename the name of the file being imported
     * @param in       the data to import
     * @throws Exception in case of an error during the import
     */
    protected abstract void executeForStream(String filename, Producer<InputStream> in) throws Exception;

    /**
     * Determines if the given file extension can be handled by the import job.
     *
     * @param fileExtension the file extension to check (this is guaranteed to be lowercase).
     * @return <tt>true</tt> if it can be handled, <tt>false</tt> otherwise
     */
    protected abstract boolean canHandleFileExtension(@Nullable String fileExtension);
}
