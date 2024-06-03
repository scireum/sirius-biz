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

import java.util.List;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a {@link LineBasedImportJob}.
 */
public abstract class LineBasedImportJobFactory extends FileImportJobFactory {

    /**
     * Specifies the file extensions supported by this factory.
     */
    protected static final List<String> SUPPORTED_FILE_EXTENSIONS = List.of("xls", "xlsx", "csv");

    @Override
    protected abstract LineBasedImportJob createJob(ProcessContext process);

    @Override
    protected void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer) {
        SUPPORTED_FILE_EXTENSIONS.forEach(fileExtensionConsumer);
    }

    @Override
    public String getIcon() {
        return "fa-regular fa-file-excel";
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(LineBasedImportJob.IMPORT_ALL_SHEETS_PARAMETER);
    }
}
