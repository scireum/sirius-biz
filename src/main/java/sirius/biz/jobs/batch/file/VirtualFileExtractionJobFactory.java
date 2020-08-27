/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.JobCategory;
import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.util.UnzipHelper;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a factory for file extraction.
 */
@Register
public class VirtualFileExtractionJobFactory extends DefaultBatchProcessFactory {

    public static final FileParameter SOURCE_PARAMETER = new FileParameter("sourceParameter",
                                                                           "$VirtualFileExtractionJobFactory.sourceParameter")
            .withAcceptedExtensionsList(UnzipHelper.getSupportedFileExtensions())
            .withDescription("$VirtualFileExtractionJobFactory.sourceParameter.help")
            .markRequired();

    public static final FileOrDirectoryParameter DESTINATION_PARAMETER = new FileOrDirectoryParameter(
            "destinationParameter",
            "$VirtualFileExtractionJobFactory.destinationParameter").withDescription(
            "$VirtualFileExtractionJobFactory.destinationParameter.help");

    public static final BooleanParameter OVERWRITE_EXISTING_FILES_PARAMETER = new BooleanParameter(
            "overwriteExistingFilesParameter",
            "$VirtualFileExtractionJobFactory.overwriteExistingFilesParameter").withDescription(
            "$VirtualFileExtractionJobFactory.overwriteExistingFilesParameter.help");

    @Override
    protected void computePresetFor(@Nonnull QueryString queryString,
                                    @Nullable Object targetObject,
                                    Map<String, Object> preset) {
        preset.put(SOURCE_PARAMETER.getName(), ((VirtualFile) targetObject).path());
    }

    @Override
    protected boolean hasPresetFor(@Nonnull QueryString queryString, @Nullable Object targetObject) {
        return targetObject instanceof VirtualFile
               && UnzipHelper.isArchiveFile(Files.getFileExtension(targetObject.toString()));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(SOURCE_PARAMETER);
        parameterCollector.accept(DESTINATION_PARAMETER);
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
        return PersistencePeriod.ONE_DAY;
    }

    @Override
    protected BatchJob createJob(ProcessContext process) throws Exception {
        return new VirtualFileExtractionJob(process);
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_MISC;
    }

    @Nonnull
    @Override
    public String getName() {
        return "file-extraction";
    }
}
