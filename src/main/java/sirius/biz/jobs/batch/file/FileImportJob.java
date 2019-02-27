/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ImportJob;
import sirius.biz.jobs.params.VirtualObjectParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.Storage;
import sirius.biz.storage.VirtualObject;
import sirius.kernel.di.std.Part;

import java.io.InputStream;

/**
 * Provides an import job which reads and imports a file.
 */
public abstract class FileImportJob extends ImportJob {

    @Part
    private static Storage storage;

    private FileImportJobFactory factory;
    private VirtualObjectParameter fileParameter;

    /**
     * Creates a new job for the given factory and process context.
     *
     * @param fileParameter the parameter which is used to derive the import file from
     * @param process       the process context in which the job is executed
     */
    protected FileImportJob(VirtualObjectParameter fileParameter, ProcessContext process) {
        super(process);
        this.fileParameter = fileParameter;
    }

    @Override
    public void execute() throws Exception {
        VirtualObject file = process.require(fileParameter);
        try (InputStream in = storage.getData(file)) {
            executeForStream(file.getFilename(), in);
        }
    }

    /**
     * Actually performs the import for the given input stream.
     *
     * @param filename the name of the file being imported
     * @param in       the data to import
     * @throws Exception in case of an error during the import
     */
    protected abstract void executeForStream(String filename, InputStream in) throws Exception;
}
