/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;
import sirius.biz.storage.VirtualObject;
import sirius.web.data.LineBasedProcessor;

/**
 * Provides a base implementation for batch jobs which import line based files using a {@link LineBasedImportJob}.
 */
public abstract class LineBasedImportJobFactory extends FileImportJobFactory {

    @Override
    protected abstract LineBasedImportJob createJob(ProcessContext process);

    /**
     * Uses the given file and creates a {@link LineBasedProcessor} for it.
     *
     * @param ctx the current process context
     * @return the processor which is appropriate to read the given file
     */
    protected LineBasedProcessor createProcessor(ProcessContext ctx) {
        VirtualObject file = ctx.require(fileParameter);
        return LineBasedProcessor.create(file.getFilename(), storage.getData(file));
    }

    @Override
    public String getIcon() {
        return "fa-file-excel-o";
    }
}
