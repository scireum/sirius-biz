/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.VirtualFile;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which transform line based files.
 * <p>
 * Using a {@link LineBasedImportExportJob} a line based file is read line by line and can write lines
 * into the provided (line based) output file.
 */
public abstract class LineBasedImportExportJobFactory extends LineBasedImportJobFactory {

    @Override
    protected abstract LineBasedImportExportJob createJob(ProcessContext process);

    /**
     * Can be overwritten to customize the destination parameter.
     *
     * @return the parameter used to select the destination of the generated file
     */
    protected Parameter<VirtualFile> getDestinationParameter() {
        return FileExportJob.createDestinationParameter(LineBasedImportJobFactory.SUPPORTED_FILE_EXTENSIONS);
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(getDestinationParameter());
        parameterCollector.accept(LineBasedExportJob.FILE_TYPE_PARAMETER);
    }
}
