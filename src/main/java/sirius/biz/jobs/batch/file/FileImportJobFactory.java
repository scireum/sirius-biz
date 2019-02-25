/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ImportBatchProcessFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.VirtualObjectParameter;
import sirius.biz.storage.Storage;
import sirius.biz.storage.VirtualObject;
import sirius.kernel.di.std.Part;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import files.
 */
public abstract class FileImportJobFactory extends ImportBatchProcessFactory {

    @Part
    protected Storage storage;

    /**
     * Contains the parameter which is used to select the file (as <tt>VirtualObject</tt>) out of the WORK bucket.
     */
    protected final VirtualObjectParameter fileParameter = new VirtualObjectParameter("file").markRequired();

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + fileParameter.get(context).map(VirtualObject::toString).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(fileParameter);
    }
}
