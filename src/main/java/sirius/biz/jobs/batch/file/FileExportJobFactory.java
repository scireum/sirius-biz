/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ExportBatchProcessFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.VirtualFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which export data into a file.
 */
public abstract class FileExportJobFactory extends ExportBatchProcessFactory {

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + FileExportJob.DESTINATION_PARAMETER.get(context)
                                                               .filter(VirtualFile::isFile)
                                                               .map(VirtualFile::toString)
                                                               .map(filename -> ": " + filename)
                                                               .orElse("");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(getDestinationParameter());
    }

    /**
     * Can be overwritten to create a customer parameter to select the export destination.
     * <p>
     * Note that this should use {@link FileExportJob#createDestinationParameter(List)} to ensure that
     * the custom parameter and the actual parameter used to retrieve the value match properly.
     *
     * @return the parameter used to select the export destination
     */
    protected Parameter<VirtualFile> getDestinationParameter() {
        List<String> fileExtensions = new ArrayList<>();
        collectAcceptedFileExtensions(fileExtensions::add);

        return FileExportJob.createDestinationParameter(fileExtensions);
    }

    /**
     * Collects the supported file extensions for the export destination files.
     *
     * @param fileExtensionConsumer a collector to be supplied with all supported file extensions
     */
    protected abstract void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer);

    @Override
    protected abstract FileExportJob createJob(ProcessContext process);
}
