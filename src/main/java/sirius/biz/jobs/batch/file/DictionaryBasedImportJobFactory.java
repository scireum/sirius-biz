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
 * Provides a base implementation for batch jobs which import line based files using a
 * {@link sirius.biz.importer.format.ImportDictionary}.
 */
public abstract class DictionaryBasedImportJobFactory extends LineBasedImportJobFactory {

    /**
     * Contains the parameter which is used to determine if empty values should be ignored).
     */
    protected final BooleanParameter ignoreEmptyParameter = createIgnoreEmptyParameter();

    /**
     * Creates the parameter which determines if empty rows should be ignored.
     * <p>
     * This is provided as a helper method so that other / similar jobs can re-use it.
     * We do not re-use the same parameter, as a parameter isn't immutable, so a global constant could
     * be easily set into an inconsistent state.
     *
     * @return the completely initialized parameter.
     */
    public static BooleanParameter createIgnoreEmptyParameter() {
        return new BooleanParameter("ignoreEmpty", "$DictionaryBasedImportJobFactory.ignoreEmpty").withDescription(
                "$DictionaryBasedImportJobFactory.ignoreEmpty.help");
    }

    @Override
    protected abstract DictionaryBasedImportJob createJob(ProcessContext process);

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(ignoreEmptyParameter);
    }
}
