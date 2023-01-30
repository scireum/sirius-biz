/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.web.data.LineBasedProcessor;
import sirius.web.data.RowProcessor;

import java.io.InputStream;
import java.util.Set;

/**
 * Provides a job for importing line based files (CSV, Excel).
 */
public abstract class LineBasedImportJob extends FileImportJob implements RowProcessor {

    private static final Set<String> POSSIBLE_EXCEL_FILE_EXTENSIONS = Set.of("xls", "xlsx", "zip");

    /**
     * Contains the parameter which is used to determine if empty values should be ignored).
     */
    public static final Parameter<Boolean> IMPORT_ALL_SHEETS_PARAMETER;

    static {
        BooleanParameter parameter =
                new BooleanParameter("importAllSheets", "$LineBasedImportJobFactory.importAllSheets");
        parameter.withDescription("$LineBasedImportJobFactory.importAllSheets.help");
        parameter.hideWhen(params -> FILE_PARAMETER.get(params)
                                                   .map(VirtualFile::fileExtension)
                                                   .filter(Strings::isFilled)
                                                   .map(String::toLowerCase)
                                                   .filter(POSSIBLE_EXCEL_FILE_EXTENSIONS::contains)
                                                   .isEmpty());

        IMPORT_ALL_SHEETS_PARAMETER = parameter.build();
    }

    private static final String ERROR_CONTEXT_ROW = "$LineBasedJob.row";

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param process the process context itself
     */
    protected LineBasedImportJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected void executeForStream(String filename, Producer<InputStream> inputSupplier) throws Exception {
        try (InputStream in = inputSupplier.create()) {
            LineBasedProcessor.create(filename, in, process.getParameter(IMPORT_ALL_SHEETS_PARAMETER).orElse(false))
                              .run((rowNumber, row) -> {
                                  errorContext.withContext(ERROR_CONTEXT_ROW, rowNumber);
                                  this.handleRow(rowNumber, row);
                                  errorContext.removeContext(ERROR_CONTEXT_ROW);
                              }, error -> {
                                  process.handle(error);
                                  errorContext.removeContext(ERROR_CONTEXT_ROW);

                                  return true;
                              });
        }
    }

    @Override
    protected boolean canHandleFileExtension(String fileExtension) {
        if (Strings.isEmpty(fileExtension)) {
            return false;
        }

        return Value.of(fileExtension).lowerCase().in(LineBasedImportJobFactory.SUPPORTED_FILE_EXTENSIONS.toArray());
    }
}
