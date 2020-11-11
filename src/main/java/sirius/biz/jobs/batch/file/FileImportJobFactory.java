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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import files.
 */
public abstract class FileImportJobFactory extends ImportBatchProcessFactory {

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + FileImportJob.FILE_PARAMETER.get(context).map(VirtualFile::name).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(createFileParameter());
    }

    /**
     * Can be overwritten to create a custom parameter to select the input file.
     * <p>
     * Note that this should use {@link FileImportJob#createFileParameter(List)} to ensure that the custom
     * parameter and the one use to retrieve the value match properly.
     *
     * @return the effective parameter to select the import file
     */
    protected Parameter<VirtualFile> createFileParameter() {
        List<String> fileExtensions = new ArrayList<>();
        collectAcceptedFileExtensions(fileExtensions::add);

        return FileImportJob.createFileParameter(fileExtensions);
    }

    /**
     * Collects the supported file extensions to be imported.
     *
     * @param fileExtensionConsumer a collector to be supplied with all supported file extensions
     */
    protected abstract void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer);
}
