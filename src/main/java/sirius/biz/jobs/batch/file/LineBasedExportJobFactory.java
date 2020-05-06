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

    protected final EnumParameter<ExportFileType> fileTypeParameter = createFileTypeParameter();

    /**
     * Creates the parameter which determines the output file type to generate.
     * <p>
     * This is provided as a helper method so that other / similar jobs can re-use it.
     * We do not re-use the same parameter, as a parameter isn't immutable, so a global constant could
     * be easily set into an inconsistent state.
     *
     * @return the completely initialized parameter.
     */
    protected static EnumParameter<ExportFileType> createFileTypeParameter() {
        EnumParameter<ExportFileType> fileTypeParameter =
                new EnumParameter<>("fileType", "$LineBasedExportJobFactory.fileType", ExportFileType.class);
        fileTypeParameter.withDefault(ExportFileType.XLSX);
        fileTypeParameter.withDescription("$LineBasedExportJobFactory.fileType.help");
        fileTypeParameter.markRequired();
        return fileTypeParameter;
    }

    protected LineBasedExportJobFactory() {
        // Makes the constructor protected as this is an abstract class
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
