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
    protected void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer) {
        LineBasedImportJobFactory.SUPPORTED_FILE_EXTENSIONS.forEach(fileExtensionConsumer);
    }

}
