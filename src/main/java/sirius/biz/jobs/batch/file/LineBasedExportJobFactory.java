/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which export line based files using a {@link LineBasedExportJob}.
 */
public abstract class LineBasedExportJobFactory extends FileExportJobFactory {

    protected final EnumParameter<ExportFileType> fileTypeParameter;

    protected LineBasedExportJobFactory() {
        fileTypeParameter =
                new EnumParameter<>("fileType", "$LineBasedExportJobFactory.fileType", ExportFileType.class);
        fileTypeParameter.withDefault(ExportFileType.XLSX);
        fileTypeParameter.withDescription("$LineBasedExportJobFactory.fileType.help");
        fileTypeParameter.markRequired();
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(fileTypeParameter);
    }

    @Override
    protected abstract LineBasedExportJob createJob(ProcessContext process);

    @Override
    public String getIcon() {
        return "fa-file-excel-o";
    }
}
