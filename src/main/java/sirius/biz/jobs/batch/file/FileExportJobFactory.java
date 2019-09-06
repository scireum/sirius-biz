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
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.VirtualFile;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which export data into a file.
 */
public abstract class FileExportJobFactory extends ExportBatchProcessFactory {

    protected final FileOrDirectoryParameter destinationParameter;

    protected FileExportJobFactory() {
        destinationParameter = new FileOrDirectoryParameter("destination", "$FileExportJobFactory.destination");
        destinationParameter.withDescription("$FileExportJobFactory.destination.help");
        destinationParameter.withBasePath("/work");
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + destinationParameter.get(context).map(VirtualFile::toString).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(destinationParameter);
    }

    @Override
    protected abstract FileExportJob createJob(ProcessContext process);
}
