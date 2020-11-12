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
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.web.data.LineBasedProcessor;
import sirius.web.data.RowProcessor;

import java.io.InputStream;

/**
 * Provides a job for importing line based files (CSV, Excel).
 */
public abstract class LineBasedImportJob extends FileImportJob implements RowProcessor {

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param process the process context itself
     */
    protected LineBasedImportJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        LineBasedProcessor.create(filename, in).run(this, error -> {
            process.handle(error);
            return true;
        });
    }

    @Override
    protected boolean canHandleFileExtension(String fileExtension) {
        if (Strings.isEmpty(fileExtension)) {
            return false;
        }

        return Value.of(fileExtension).in("xls", "xlsx", "csv");
    }
}
