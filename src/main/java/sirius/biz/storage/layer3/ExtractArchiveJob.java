/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.util.ArchiveExtractor;
import sirius.biz.util.ExtractedFile;
import sirius.kernel.async.TaskContext;
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
 * The following file extensions are supported: {@link ArchiveHelper#getSupportedFileExtensions()}.
 */
@Register
public class ExtractArchiveJob extends SimpleBatchProcessJobFactory {

    @Part
    private VirtualFileSystem vfs;

    @Part
    private ArchiveExtractor extractor;

    private final Parameter<VirtualFile> sourceParamter;
    private final Parameter<VirtualFile> destinationParameter;
    private final Parameter<ArchiveExtractor.OverrideMode> overwriteExistingFilesParameter;
    private final Parameter<Boolean> deleteArchiveParameter;

    /**
     * Creates the job factory so that it can be invoked by the framework.
     * <p>
     * This constructor is primarily used to make the parameter instantiation more readable.
     */
    public ExtractArchiveJob() {
        this.sourceParamter = new FileParameter("source",
                                                "$ExtractArchiveJob.sourceParameter").withAcceptedExtensionsList(new ArrayList<>(
                extractor.getSupportedFileExtensions()))
                                                                                     .withDescription(
                                                                                             "$ExtractArchiveJob.sourceParameter.help")
                                                                                     .markRequired()
                                                                                     .build();

        this.destinationParameter =
                new DirectoryParameter("destination", "$ExtractArchiveJob.destinationParameter").withDescription(
                        "$ExtractArchiveJob.destinationParameter.help").markRequired().build();

        this.overwriteExistingFilesParameter = new EnumParameter<>("overwriteExistingFiles",
                                                                   "$ExtractArchiveJob.overwriteExistingFilesParameter",
                                                                   ArchiveExtractor.OverrideMode.class).withDescription(
                "$ExtractArchiveJob.overwriteExistingFilesParameter.help")
                                                                                                       .withDefault(
                                                                                                               ArchiveExtractor.OverrideMode.ON_CHANGE)
                                                                                                       .build();

        this.deleteArchiveParameter =
                new BooleanParameter("deleteArchive", "$ExtractArchiveJob.deleteArchiveParameter").withDescription(
                        "$ExtractArchiveJob.deleteArchiveParameter.help").withDefaultTrue().build();
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        VirtualFile sourceFile = process.require(sourceParamter);
        VirtualFile targetDirectory = process.require(destinationParameter);
        ArchiveExtractor.OverrideMode overrideMode = process.require(overwriteExistingFilesParameter);

        process.log(ProcessLog.info()
                              .withNLSKey("FileImportJob.downloadingFile")
                              .withContext("file", sourceFile.name())
                              .withContext("size", NLS.formatSize(sourceFile.size())));

        try (FileHandle archive = sourceFile.download()) {
            extractor.extractAll(sourceFile.name(),
                                 archive.getFile(),
                                 null,
                                 file -> handleExtractedFile(file, process, overrideMode, targetDirectory));
        }

        process.setState(NLS.get("ExtractArchiveJob.completed"));

        if (!process.isErroneous() && process.require(deleteArchiveParameter).booleanValue()) {
            process.log(ProcessLog.info().withNLSKey("ExtractArchiveJob.deletingArchive"));
            sourceFile.delete();
        }
    }

    private void handleExtractedFile(ExtractedFile extractedFile,
                                     ProcessContext process,
                                     ArchiveExtractor.OverrideMode overrideMode,
                                     VirtualFile targetDirectory) throws Exception {
        updateState(extractedFile);

        Watch watch = Watch.start();
        VirtualFile targetFile = targetDirectory.resolve(extractedFile.getFilePath());
        ArchiveExtractor.UpdateResult result = extractor.updateFile(extractedFile, targetFile, overrideMode);
        switch (result) {
            case CREATED:
                process.addTiming(NLS.get("ExtractArchiveJob.fileCreated"), watch.elapsedMillis());
                break;
            case UPDATED:
                process.addTiming(NLS.get("ExtractArchiveJob.fileOverwritten"), watch.elapsedMillis());
                break;
            case SKIPPED:
                process.addTiming(NLS.get("ExtractArchiveJob.fileSkipped"), watch.elapsedMillis());
                break;
            default:
                throw new IllegalArgumentException(result.name());
        }

        log(process, extractedFile, targetFile, result.name());
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

    private void updateState(ExtractedFile extractedFile) {
        TaskContext taskContext = TaskContext.get();
        if (taskContext.shouldUpdateState().check()) {
            taskContext.setState(NLS.fmtr("$ExtractArchiveJob.progress")
                                    .set("progress", extractedFile.getProgressInPercent().toPercentString())
                                    .format());
        }
    }

    @Override
    protected void computePresetFor(@Nonnull QueryString queryString,
                                    @Nullable Object targetObject,
                                    Map<String, Object> preset) {
        preset.put(sourceParamter.getName(), ((VirtualFile) targetObject).path());
    }

    @Override
    protected boolean hasPresetFor(@Nonnull QueryString queryString, @Nullable Object targetObject) {
        return targetObject instanceof VirtualFile
               && extractor.isArchiveFile(Files.getFileExtension(targetObject.toString()));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(sourceParamter);
        parameterCollector.accept(destinationParameter);
        parameterCollector.accept(overwriteExistingFilesParameter);
        parameterCollector.accept(deleteArchiveParameter);
    }

    @Override
    public String getIcon() {
        return "fa-file-archive-o";
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return Strings.apply("%s (%s)", getLabel(), sourceParamter.require(context).name());
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
