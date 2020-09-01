/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import com.google.common.io.ByteSource;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.biz.util.ArchiveHelper;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.http.QueryString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Provides a job able to extract archives from the {@link VirtualFileSystem}. The following file extensions are supported: {@link ArchiveHelper#getSupportedFileExtensions()}.
 */
@Register
public class VirtualFileExtractionJob extends SimpleBatchProcessJobFactory {

    @Part
    private VirtualFileSystem vfs;

    @Part
    private ArchiveHelper archiveHelper;

    private static final FileParameter SOURCE_PARAMETER = new FileParameter("sourceParameter",
                                                                            "$VirtualFileExtractionJob.sourceParameter")
            .withAcceptedExtensionsList(ArchiveHelper.getSupportedFileExtensions())
            .withDescription("$VirtualFileExtractionJob.sourceParameter.help")
            .markRequired();

    private static final FileOrDirectoryParameter DESTINATION_PARAMETER = new FileOrDirectoryParameter(
            "destinationParameter",
            "$VirtualFileExtractionJob.destinationParameter").withDescription(
            "$VirtualFileExtractionJob.destinationParameter.help");

    private static final BooleanParameter OVERWRITE_EXISTING_FILES_PARAMETER = new BooleanParameter(
            "overwriteExistingFilesParameter",
            "$VirtualFileExtractionJob.overwriteExistingFilesParameter").withDescription(
            "$VirtualFileExtractionJob.overwriteExistingFilesParameter.help");
    private static final String TARGET_PATH = "targetPath";

    @Override
    protected void execute(ProcessContext process) throws Exception {
        VirtualFile sourceFile = process.require(SOURCE_PARAMETER);
        Optional<VirtualFile> destinationDirectory = DESTINATION_PARAMETER.get(process.getContext());
        boolean shouldOverwriteExisting = process.require(OVERWRITE_EXISTING_FILES_PARAMETER);

        // by default we'll use the files directory to extract to
        final VirtualFile targetDirectory =
                destinationDirectory.orElseGet(() -> vfs.resolve(sourceFile.parent().path()));

        sourceFile.tryDownload()
                  .ifPresent(handle -> handleArchiveExtraction(process,
                                                               handle,
                                                               shouldOverwriteExisting,
                                                               targetDirectory));
    }

    private void handleArchiveExtraction(ProcessContext process,
                                         FileHandle fileHandle,
                                         boolean shouldOverwriteExisting,
                                         VirtualFile targetDirectory) {
        File tempFile = fileHandle.getFile();
        if (!TaskContext.get().isActive()) {
            Files.delete(tempFile);
            return;
        }

        try {
            ArchiveHelper.extract(tempFile,
                                  null,
                                  handleFileInArchive(process, shouldOverwriteExisting, targetDirectory));
        } catch (IOException e) {
            process.handle(e);
        } finally {
            Files.delete(tempFile);
        }
    }

    @Nonnull
    private Predicate<ArchiveHelper.ExtractionProgress> handleFileInArchive(ProcessContext process,
                                                                            boolean shouldOverwriteExisting,
                                                                            VirtualFile targetDirectory) {
        return progress -> {
            if (!TaskContext.get().isActive()) {
                return false;
            }
            updateState(progress);
            VirtualFile targetFile = vfs.resolve(vfs.makePath(targetDirectory.path(), progress.getFilePath()));
            if (targetFile.exists() && !shouldOverwriteExisting) {
                process.log(ProcessLog.info()
                                      .withMessage(NLS.fmtr("VirtualFileExtractionJob.skippingOverwrite")
                                                      .set(TARGET_PATH, targetFile.path())
                                                      .format()));
                return true;
            }

            uploadFile(process, shouldOverwriteExisting, progress, targetFile);

            return true;
        };
    }

    private void uploadFile(ProcessContext process,
                            boolean shouldOverwriteExisting,
                            ArchiveHelper.ExtractionProgress extractionProgress,
                            VirtualFile targetFile) {
        try {
            ByteSource data = extractionProgress.getData();
            if (targetFile.exists() && shouldOverwriteExisting) {
                process.log(ProcessLog.info()
                                      .withMessage(NLS.fmtr("VirtualFileExtractionJob.overwritingFile")
                                                      .set("filePath", extractionProgress.getFilePath())
                                                      .set(TARGET_PATH, targetFile.path())
                                                      .set("fileSize", NLS.formatSize(data.size()))
                                                      .format()));
            } else {
                process.log(ProcessLog.info()
                                      .withMessage(NLS.fmtr("VirtualFileExtractionJob.extractingFile")
                                                      .set("filePath", extractionProgress.getFilePath())
                                                      .set(TARGET_PATH, targetFile.path())
                                                      .set("fileSize", NLS.formatSize(data.size()))
                                                      .format()));
            }

            try (InputStream inputStream = data.openStream()) {
                targetFile.consumeStream(inputStream, data.size());
            }
        } catch (IOException e) {
            process.handle(e);
        }
    }

    private void updateState(ArchiveHelper.ExtractionProgress extractionProgress) {
        if (TaskContext.get().shouldUpdateState().check()) {
            TaskContext.get()
                       .setState(NLS.fmtr("VirtualFileExtractionJob.progress")
                                    .set("status", extractionProgress.getExtractOperationResult())
                                    .set("filesProcessed", extractionProgress.getFilesProcessed())
                                    .set("dataProcessed", NLS.formatSize(extractionProgress.getBytesProcessed()))
                                    .set("sizeTotal", NLS.formatSize(extractionProgress.getTotalBytes()))
                                    .format());
        }
    }

    @Override
    protected void computePresetFor(@Nonnull QueryString queryString,
                                    @Nullable Object targetObject,
                                    Map<String, Object> preset) {
        preset.put(SOURCE_PARAMETER.getName(), ((VirtualFile) targetObject).path());
    }

    @Override
    protected boolean hasPresetFor(@Nonnull QueryString queryString, @Nullable Object targetObject) {
        return targetObject instanceof VirtualFile
               && ArchiveHelper.isArchiveFile(Files.getFileExtension(targetObject.toString()));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(SOURCE_PARAMETER);
        parameterCollector.accept(DESTINATION_PARAMETER);
        parameterCollector.accept(OVERWRITE_EXISTING_FILES_PARAMETER);
    }

    @Override
    public String getIcon() {
        return "fa-file-archive-o";
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        MutableVirtualFile sourceArchive = (MutableVirtualFile) SOURCE_PARAMETER.require(context);
        return Strings.apply("%s (%s)", getLabel(), sourceArchive.name());
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
