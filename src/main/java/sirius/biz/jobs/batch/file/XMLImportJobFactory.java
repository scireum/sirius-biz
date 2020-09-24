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

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import XML files using a {@link XMLImportJob}.
 */
public abstract class XMLImportJobFactory extends FileImportJobFactory {

    protected final BooleanParameter requireValidFile =
            new BooleanParameter("requireValidFile", "$XMLImportJobFactory.requireValidFile").hidden()
                                                                                                       .logInSystem();

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(requireValidFile);
        super.collectParameters(parameterCollector);
    }

    @Override
    protected abstract XMLImportJob createJob(ProcessContext process);

    @Override
    public String getIcon() {
        return "fa-code";
    }
}
