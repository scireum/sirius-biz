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
import sirius.biz.storage.VirtualObject;
import sirius.web.data.LineBasedProcessor;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a {@link LineBasedImportJob}.
 */
public abstract class LineBasedImportJobFactory extends FileImportJobFactory {

    /**
     * Contains the parameter which is used to determine if empty values should be ignored).
     */
    protected final BooleanParameter ignoreEmptyParameter =
            new BooleanParameter("ignoreEmpty", "$LineBasedImportJobFactory.ignoreEmpty").withDescription(
                    "$LineBasedImportJobFactory.ignoreEmpty.help");

    @Override
    protected abstract LineBasedImportJob<?> createJob(ProcessContext process);

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
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(ignoreEmptyParameter);
    }

    @Override
    public String getIcon() {
        return "fa-file-excel-o";
    }
}
