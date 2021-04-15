/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ImportJob;
import sirius.biz.jobs.params.BooleanParameter;
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
import sirius.kernel.commons.Monoflop;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Provides an import job which reads and imports a file.
 */
public abstract class FileImportJob extends ImportJob {

    private static final String FILE_NAME_KEY = "filename";
    private String currentFileName;
    private final LinkedHashMap<String, Boolean> entriesToExtract = new LinkedHashMap<>();

    /**
     * Contains the parameter which selects the file to import.
     * <p>
     * Note that even if another instance is used in {@link FileImportJobFactory#collectParameters(Consumer)}, this
     * will still work out as long as the parameter names are the same. Therefore both parameters should be
     * created using {@link #createFileParameter(List)}.
     */
    public static final Parameter<VirtualFile> FILE_PARAMETER = createFileParameter(null);

    /**
     * Determines the mode in which auxiliary files are handled (if this job supports it).
     */
    public static final Parameter<AuxiliaryFileMode> AUX_FILE_MODE_PARAMETER = new EnumParameter<>("auxFileMode",
                                                                                                   "$FileImportJobFactory.auxFileMode",
                                                                                                   AuxiliaryFileMode.class)
            .withDefault(AuxiliaryFileMode.UPDATE_ON_CHANGE)
            .markRequired()
            .build();

    /**
     * Determines if auxiliary files are extracted including directories (if this job supports it).
     */
    public static final Parameter<Boolean> AUX_FILE_FLATTEN_DIRS_PARAMETER = new BooleanParameter("auxFileFlattenDirs",
                                                                                                  "$FileImportJobFactory.auxFileFlattenDirectoriesParameter")
            .withDescription("$FileImportJobFactory.auxFileFlattenDirectoriesParameter.help")
            .build();

    @Part
    private static VirtualFileSystem virtualFileSystem;

    @Part
    private static ArchiveExtractor extractor;

    protected FileParameter fileParameter;
    protected ValueHolder<VirtualFile> auxFilesDestination;
    protected AuxiliaryFileMode auxiliaryFileMode;
    protected boolean flattenAuxiliaryFileDirs;

    /**
     * Defines the modes in which an auxiliary file in an archive can be handled.
     */
    enum AuxiliaryFileMode {
        /**
         * All auxiliary files will be ignored.
         */
        IGNORE(ArchiveExtractor.OverrideMode.NEVER),

        /**
         * Enforce an update of auxiliary files.
         *
         * @see sirius.biz.util.ArchiveExtractor.OverrideMode#ALWAYS
         */
        ALWAYS_UPDATE(ArchiveExtractor.OverrideMode.ALWAYS),

        /**
         * Perform an update of auxiliary files if they changed.
         *
         * @see sirius.biz.util.ArchiveExtractor.OverrideMode#ON_CHANGE
         */
        UPDATE_ON_CHANGE(ArchiveExtractor.OverrideMode.ON_CHANGE),

        /**
         * Never update auxiliary files.
         *
         * @see sirius.biz.util.ArchiveExtractor.OverrideMode#NEVER
         */
        NEVER_UPDATE(ArchiveExtractor.OverrideMode.NEVER);

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

    /**
     * Helps to create a custom file parameter with an appropriate file extension filter.
     *
     * @param acceptedFileExtensions the file extensions to accept
     * @return the parameter used to select the import file
     */
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
        flattenAuxiliaryFileDirs = process.getParameter(AUX_FILE_FLATTEN_DIRS_PARAMETER).orElse(false);

        if (canHandleFileExtension(Value.of(file.fileExtension()).toLowerCase())) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.downloadingFile")
                                  .withContext("file", file.name())
                                  .withContext("size", NLS.formatSize(file.size())));

            try (FileHandle fileHandle = file.download()) {
                currentFileName = file.name();
                backupInputFile(currentFileName, fileHandle);
                executeForStream(currentFileName, fileHandle::getInputStream);
            }
        } else if (extractor.isArchiveFile(file.fileExtension())) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.downloadingFile")
                                  .withContext("file", file.name())
                                  .withContext("size", NLS.formatSize(file.size())));

            try (FileHandle fileHandle = file.download()) {
                backupInputFile(file.name(), fileHandle);
                defineEntriesToExtract();
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
        if (entriesToExtract.isEmpty()) {
            extractAllEntries(filename, fileHandle, filesImported::incrementAndGet);
        } else {
            extractEntriesFromList(filename, fileHandle, filesImported::incrementAndGet);
        }

        if (filesImported.get() == 0) {
            throw Exceptions.createHandled().withNLSKey("FileImportJob.noZippedFileFound").handle();
        }
    }

    private void extractAllEntries(String filename, FileHandle fileHandle, Runnable counter) {
        extractor.extractAll(filename, fileHandle.getFile(), null, file -> {
            if (executeForEntry(file)) {
                counter.run();
            }
        });
    }

    private void extractEntriesFromList(String filename, FileHandle fileHandle, Runnable counter) {
        entriesToExtract.forEach((fileName, fileRequired) -> {
            if (!process.isActive()) {
                return;
            }
            Monoflop entryFound = Monoflop.create();
            extractor.extractAll(filename, fileHandle.getFile(), entryName -> {
                return entryName.equals(fileName);
            }, file -> {
                if (executeForEntry(file)) {
                    counter.run();
                }
                entryFound.toggle();
            });
            if (entryFound.isToggled()) {
                return;
            }

            if (Boolean.TRUE.equals(fileRequired)) {
                throw Exceptions.createHandled()
                                .withNLSKey("FileImportJob.requiredFileNotFound")
                                .set(FILE_NAME_KEY, fileName)
                                .handle();
            } else {
                process.log(ProcessLog.info()
                                      .withNLSKey("FileImportJob.requiredFileNotFound")
                                      .withContext(FILE_NAME_KEY, fileName));
            }
        });
    }

    /**
     * Specifies specific entries to extract from an archive.
     * <p>
     * Override this method in order to filter specific entries.
     *
     * @see #addEntryFilter(String, boolean)
     */
    protected void defineEntriesToExtract() {
        // By default all entries are extracted.
    }

    /**
     * Adds an entry to filter when extracting files from an archive.
     *
     * @param filename     the filename to filter
     * @param fileRequired specifies if the file must exist in the archive
     */
    protected void addEntryFilter(String filename, boolean fileRequired) {
        entriesToExtract.put(filename, fileRequired);
    }

    private boolean executeForEntry(ExtractedFile extractedFile) throws Exception {
        if (canHandleFileExtension(Files.getFileExtension(extractedFile.getFilePath()))) {
            process.log(ProcessLog.info()
                                  .withNLSKey("FileImportJob.importingZippedFile")
                                  .withContext(FILE_NAME_KEY, extractedFile.getFilePath()));
            currentFileName = extractedFile.getFilePath();
            executeForStream(currentFileName, extractedFile::openInputStream);
            return true;
        } else if (auxiliaryFileMode != AuxiliaryFileMode.IGNORE) {
            return handleAuxiliaryFile(extractedFile);
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
    protected boolean handleAuxiliaryFile(ExtractedFile extractedFile) {
        try {
            Watch watch = Watch.start();
            VirtualFile basePath = determineAuxiliaryFilesBasePath();
            if (basePath == null) {
                return false;
            }

            VirtualFile targetFile = basePath.resolve(getTargetPath(extractedFile));
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

    private String getTargetPath(ExtractedFile extractedFile) {
        String targetPath = extractedFile.getFilePath();
        if (flattenAuxiliaryFileDirs) {
            return Files.getFilenameAndExtension(targetPath);
        } else {
            return targetPath;
        }
    }

    @Nullable
    private VirtualFile determineAuxiliaryFilesBasePath() {
        if (auxFilesDestination == null) {
            String unusedFilesPath = determineAuxiliaryFilesDirectory();
            if (Strings.isEmpty(unusedFilesPath)) {
                auxFilesDestination = ValueHolder.of(null);
            } else {
                VirtualFile destination = virtualFileSystem.resolve(unusedFilesPath);
                if (destination.exists() && destination.isDirectory()) {
                    auxFilesDestination = ValueHolder.of(destination);
                } else {
                    auxFilesDestination = ValueHolder.of(null);
                }
            }
        }

        return auxFilesDestination.get();
    }

    /**
     * Returns the file name currently being processed.
     * <p>
     * This method is useful when processing archives since it points to the actual entry being processed
     *
     * @return a string containing the file name
     */
    public String getCurrentFileName() {
        return currentFileName;
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
