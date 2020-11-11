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
 * Provides a base implementation for batch jobs which export line based files using a {@link LineBasedExportJob}.
 */
public abstract class LineBasedExportJobFactory extends FileExportJobFactory {

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(LineBasedExportJob.FILE_TYPE_PARAMETER);
    }

    @Override
    protected abstract LineBasedExportJob createJob(ProcessContext process);

    @Override
    protected Parameter<VirtualFile> getDestinationParameter() {
        return FileExportJob.createDestinationParameter(LineBasedImportJobFactory.SUPPORTED_FILE_EXTENSIONS);
    }

    @Override
    public String getIcon() {
        return "fa-file-excel-o";
    }
}
