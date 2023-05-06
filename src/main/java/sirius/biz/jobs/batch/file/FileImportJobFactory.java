/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ImportBatchProcessFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.util.ArchiveExtractor;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Part;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import files.
 */
public abstract class FileImportJobFactory extends ImportBatchProcessFactory {

    @Part
    private ArchiveExtractor archiveExtractor;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + FileImportJob.FILE_PARAMETER.get(context).map(VirtualFile::name).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(createFileParameter());
        if (supportsAuxiliaryFiles()) {
            parameterCollector.accept(FileImportJob.AUX_FILE_MODE_PARAMETER);
            parameterCollector.accept(FileImportJob.AUX_FILE_FLATTEN_DIRS_PARAMETER);
            if (supportsParentDirectories()) {
                parameterCollector.accept(FileImportJob.AUX_FILE_PARENT_DIRECTORY_PARAMETER);
            }
        }
        if (UserContext.getCurrentUser().hasPermission(FEATURE_BYPASS_PROCESS_LOG_LIMITS)) {
            parameterCollector.accept(LIMIT_LOG_MESSAGES_PARAMETER);
        }
    }

    /**
     * Can be overwritten to create a custom parameter to select the input file.
     * <p>
     * Note that this should use {@link FileImportJob#createFileParameter(List, String)} to ensure that the custom
     * parameter and the one used to retrieve the value match properly.
     *
     * @return the effective parameter to select the import file
     */
    protected Parameter<VirtualFile> createFileParameter() {
        List<String> fileExtensions = new ArrayList<>();
        collectAcceptedFileExtensions(fileExtensions::add);
        fileExtensions.addAll(archiveExtractor.getSupportedFileExtensions());

        return FileImportJob.createFileParameter(fileExtensions, getFileParameterDescription());
    }

    /**
     * Defines the description of the {@link FileImportJob#FILE_PARAMETER}.
     * <p>
     * Override this method in order to define a custom description, otherwise a default one is used
     *
     * @return the description in as a translatable resource.
     */
    @SuppressWarnings("squid:S3400")
    @Explain("We want allow overriding the default description")
    protected String getFileParameterDescription() {
        return "$FileImportJobFactory.file.help";
    }

    /**
     * Collects the supported file extensions to be imported.
     *
     * @param fileExtensionConsumer a collector to be supplied with all supported file extensions
     */
    protected abstract void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer);

    /**
     * Determines if jobs created by this factory support handling auxiliary files.
     * <p>
     * These are files which reside in an archive processed by this job which cannot be handled directly.
     * This might be an image attachment which cannot be processed by a <tt>LineBasedImportJob</tt>.
     * <p>
     * These files are handled by {@link FileImportJob#handleAuxiliaryFile(sirius.biz.util.ExtractedFile)}.
     *
     * @return <tt>true</tt> if this job supports processing auxiliary files, <tt>false</tt> otherwise
     */
    protected boolean supportsAuxiliaryFiles() {
        return false;
    }

    /**
     * Determines if jobs created by this factory support parent directories.
     * <p>
     * If enabled, the provided parent directory(ies) will be used between the upload root and final destination.
     * Note that we automatically detect if the archive content's path starts with the given directory and drop it.
     * Example:<br>
     * Upload Root: <tt>/work</tt><br>
     * Parent Directory: <tt>/foo/bar</tt><br>
     * Archive Contents: <tt>[file1, dir1/file2, foo/bar/file3]</tt><br>
     * Final Destination: <tt>[/work/foo/bar/file1, /work/foo/bar/dir1/file2, /work/foo/bar/file3]</tt><br>
     * <p>
     * This setting is only effective if auxiliary files are supported.
     *
     * @return <tt>true</tt> if this job supports parent directories, <tt>false</tt> otherwise
     * @see #supportsAuxiliaryFiles()
     */
    protected boolean supportsParentDirectories() {
        return false;
    }
}
