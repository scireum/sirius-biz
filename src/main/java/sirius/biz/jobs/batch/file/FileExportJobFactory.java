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

    protected Parameter<VirtualFile> getDestinationParameter() {
        return FileExportJob.DESTINATION_PARAMETER;
    }

    @Override
    protected abstract FileExportJob createJob(ProcessContext process);
}
