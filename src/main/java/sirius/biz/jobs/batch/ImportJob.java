/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.importer.Importer;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;

import java.io.IOException;

/**
 * Provides a base class for batch jobs which utilize an {@link Importer} to import data.
 */
public abstract class ImportJob extends BatchJob {

    /**
     * Contains the importer which can be used to import data
     */
    protected final Importer importer;

    /**
     * Creates a new job for the given process context.
     *
     * @param process the process context in which the job is executed
     */
    protected ImportJob(ProcessContext process) {
        super(process);
        this.importer = new Importer(process.getTitle());
    }

    @Override
    public void close() throws IOException {
        try {
            if (importer.getContext().hasBatchContext()) {
                process.log(ProcessLog.info()
                                      .withMessage(importer.getContext().getBatchContext().toString())
                                      .asSystemMessage());
            }
            this.importer.close();
        } catch (IOException e) {
            process.handle(e);
        }
        super.close();
    }
}
