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

public abstract class ImportJob extends BatchJob {

    protected final Importer importer;

    protected ImportJob(String name, ProcessContext process) {
        super(process);
        this.importer = new Importer(name);
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
