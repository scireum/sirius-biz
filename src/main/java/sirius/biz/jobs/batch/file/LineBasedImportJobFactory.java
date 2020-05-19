/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;

/**
 * Provides a base implementation for batch jobs which import line based files using a {@link LineBasedImportJob}.
 */
public abstract class LineBasedImportJobFactory extends FileImportJobFactory {

    @Override
    protected abstract LineBasedImportJob createJob(ProcessContext process);

    @Override
    public String getIcon() {
        return "fa-file-excel-o";
    }
}
