/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.FileParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.util.ArchiveExtractor;
import sirius.biz.util.ExtractedFile;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.http.QueryString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a job able to extract archives from the {@link VirtualFileSystem}.
 * <p>
 * This uses the {@link ArchiveExtractor} so depending on if 7-ZIP is enabled this supports either a bunch
 * of formats (rar, 7z, tar etc.) or "just" ZIP files using the Java API.
 */
@Register(classes = {JobFactory.class, ExtractArchiveJob.class}, framework = StorageUtils.FRAMEWORK_STORAGE)
public class ExtractArchiveJob extends SimpleBatchProcessJobFactory {

    /**
     * Contains the name of the parameter which is used to set the destination for the extracted files.
     */
    public static final String DESTINATION_PARAMETER_NAME = "destination";

    /**
     * Determines if existing files should be overwritten.
     */
    public static final String OVERWRITE_EXISTING_FILES_PARAMETER_NAME = "overwriteExistingFiles";

    /**
     * Determines if archives should be deleted once they are processed.
     */
    public static final String DELETE_ARCHIVE_PARAMETER_NAME = "deleteArchive";

    /**
     * Determines if paths should be skipped when extracting files.
     */
    public static final String FLATTEN_DIRECTORIES_PARAMETER_NAME = "flattenDirectories";

    @Part
    private VirtualFileSystem vfs;

    @Part
    private ArchiveExtractor extractor;

    private Parameter<VirtualFile> sourceParameter;
    private final Parameter<VirtualFile> destinationParameter;
    private final Parameter<ArchiveExtractor.OverrideMode> overwriteExistingFilesParameter;
    private final Parameter<Boolean> flattenDirectoriesParameter;
    private final Parameter<Boolean> deleteArchiveParameter;

    private static final String FILE_SKIPPED_COUNTER = "ExtractArchiveJob.fileSkipped";
    private static final String FILE_EMPTY_COUNTER = "ExtractArchiveJob.fileEmpty";

    /**
     * Creates the job factory so that it can be invoked by the framework.
     * <p>
     * This constructor is primarily used to make the parameter instantiation more readable.
     */
    public ExtractArchiveJob() {
        this.destinationParameter = new FileParameter(DESTINATION_PARAMETER_NAME,
                                                      "$ExtractArchiveJob.destinationParameter").directoriesOnly()
                                                                                                .withDescription(
                                                                                                        "$ExtractArchiveJob.destinationParameter.help")
                                                                                                .build();

        this.overwriteExistingFilesParameter = new EnumParameter<>(OVERWRITE_EXISTING_FILES_PARAMETER_NAME,
                                                                   "$ExtractArchiveJob.overwriteExistingFilesParameter",
                                                                   ArchiveExtractor.OverrideMode.class).withDescription(
                                                                                                               "$ExtractArchiveJob.overwriteExistingFilesParameter.help")
                                                                                                       .withDefault(
                                                                                                               ArchiveExtractor.OverrideMode.ON_CHANGE)
                                                                                                       .build();

        this.deleteArchiveParameter = new BooleanParameter(DELETE_ARCHIVE_PARAMETER_NAME,
                                                           "$ExtractArchiveJob.deleteArchiveParameter").withDescription(
                "$ExtractArchiveJob.deleteArchiveParameter.help").withDefaultTrue().build();
        this.flattenDirectoriesParameter = new BooleanParameter(FLATTEN_DIRECTORIES_PARAMETER_NAME,
                                                                "$ExtractArchiveJob.flattenDirectoriesParameter").withDescription(
                "$ExtractArchiveJob.flattenDirectoriesParameter.help").build();
    }

    private Parameter<VirtualFile> fetchOrCreateSourceParameter() {
        if (sourceParameter == null) {
            sourceParameter = new FileParameter("source",
                                                "$ExtractArchiveJob.sourceParameter").withAcceptedExtensionsList(new ArrayList<>(
                                                                                             extractor.getSupportedFileExtensions()))
                                                                                     .withDescription(
                                                                                             "$ExtractArchiveJob.sourceParameter.help")
                                                                                     .filesOnly()
                                                                                     .markRequired()
                                                                                     .build();
        }

        return sourceParameter;
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        VirtualFile sourceFile = process.require(fetchOrCreateSourceParameter());
        VirtualFile targetDirectory = process.getParameter(destinationParameter).orElseGet(sourceFile::parent);
        boolean flattenDirs = process.getParameter(flattenDirectoriesParameter).orElse(false);
        ArchiveExtractor.OverrideMode overrideMode = process.require(overwriteExistingFilesParameter);

        process.log(ProcessLog.info()
                              .withNLSKey("FileImportJob.downloadingFile")
                              .withContext("file", sourceFile.name())
                              .withContext("size", NLS.formatSize(sourceFile.size())));

        try (FileHandle archive = sourceFile.download()) {
            extractor.extractAll(sourceFile.name(),
                                 archive.getFile(),
                                 null,
                                 file -> handleExtractedFile(file,
                                                             process,
                                                             overrideMode,
                                                             targetDirectory,
                                                             flattenDirs));
        }

        process.forceUpdateState(NLS.get("ExtractArchiveJob.completed"));

        if (!process.isErroneous() && process.require(deleteArchiveParameter).booleanValue()) {
            process.log(ProcessLog.info().withNLSKey("ExtractArchiveJob.deletingArchive"));
            sourceFile.delete();
        }
    }

    private void handleExtractedFile(ExtractedFile extractedFile,
                                     ProcessContext process,
                                     ArchiveExtractor.OverrideMode overrideMode,
                                     VirtualFile targetDirectory,
                                     boolean flattenDirectory) throws Exception {
        Watch watch = Watch.start();

        if (extractedFile == null) {
            // if this happens we don't know the file name of this entry, we just log with an empty name
            process.log(ProcessLog.warn().withNLSKey("ExtractArchiveJob.emptyFile").withContext("filename", ""));
            process.addTiming(FILE_EMPTY_COUNTER, watch.elapsedMillis());
            return;
        }

        process.tryUpdateState(NLS.fmtr("ExtractArchiveJob.progress")
                                  .set("progress", extractedFile.getProgressInPercent().toPercentString())
                                  .format());

        if (extractedFile.size() == 0) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("ExtractArchiveJob.emptyFile")
                                  .withContext("filename", extractedFile.getFilePath()));
            process.addTiming(FILE_EMPTY_COUNTER, watch.elapsedMillis());
            return;
        }

        String targetPath = getTargetPath(extractedFile, flattenDirectory);
        VirtualFile targetFile = targetDirectory.resolve(targetPath);
        ArchiveExtractor.UpdateResult result = extractor.updateFile(extractedFile, targetFile, overrideMode);
        switch (result) {
            case CREATED -> process.addTiming("ExtractArchiveJob.fileCreated", watch.elapsedMillis());
            case UPDATED -> process.addTiming("ExtractArchiveJob.fileOverwritten", watch.elapsedMillis());
            case SKIPPED -> process.addTiming(FILE_SKIPPED_COUNTER, watch.elapsedMillis());
            default -> throw new IllegalArgumentException(result.name());
        }

        log(process, extractedFile, targetFile, result.name());
    }

    private String getTargetPath(ExtractedFile extractedFile, boolean flattenDirectory) {
        String targetPath = extractedFile.getFilePath();
        if (flattenDirectory) {
            return Files.getFilenameAndExtension(targetPath);
        } else {
            return targetPath;
        }
    }

    private void log(ProcessContext process, ExtractedFile extractedFile, VirtualFile targetFile, String result) {
        process.debug(ProcessLog.info()
                                .withFormattedMessage(
                                        "Extracting file '%s' to '%s' - size: %s, last modified: %s. Result: %s",
                                        extractedFile.getFilePath(),
                                        targetFile.path(),
                                        NLS.formatSize(extractedFile.size()),
                                        NLS.toUserString(extractedFile.lastModified()),
                                        result));
    }

    @Override
    protected void computePresetFor(@Nonnull QueryString queryString,
                                    @Nullable Object targetObject,
                                    Map<String, Object> preset) {
        preset.put(fetchOrCreateSourceParameter().getName(), ((VirtualFile) targetObject).path());
    }

    @Override
    protected boolean hasPresetFor(@Nonnull QueryString queryString, @Nullable Object targetObject) {
        return targetObject instanceof VirtualFile
               && extractor.isArchiveFile(Files.getFileExtension(targetObject.toString()));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(fetchOrCreateSourceParameter());
        parameterCollector.accept(destinationParameter);
        parameterCollector.accept(overwriteExistingFilesParameter);
        parameterCollector.accept(deleteArchiveParameter);
        parameterCollector.accept(flattenDirectoriesParameter);
    }

    @Override
    public String getIcon() {
        return "far fa-file-archive";
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return Strings.apply("%s (%s)", getLabel(), fetchOrCreateSourceParameter().require(context).name());
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.THREE_YEARS;
    }

    @Nonnull
    @Override
    public String getName() {
        return "file-extraction";
    }
}
