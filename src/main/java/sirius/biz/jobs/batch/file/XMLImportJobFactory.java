/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.ProcessContext;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import XML files using a {@link XMLImportJob}.
 */
public abstract class XMLImportJobFactory extends FileImportJobFactory {

    protected final SelectStringParameter requireValidFile =
            new SelectStringParameter("requireValidFile", "$XMLImportJobFactory.requireValidFile").withDescription(
                    "$XMLImportJobFactory.requireValidFile.help").hidden();

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        if (!getXsdResourcePaths().isEmpty()) {
            getXsdResourcePaths().forEach((xsdPath, name) -> requireValidFile.withEntry(xsdPath, name));
            parameterCollector.accept(requireValidFile);
        }
        super.collectParameters(parameterCollector);
    }

    @Override
    protected abstract XMLImportJob createJob(ProcessContext process);

    @Override
    public String getIcon() {
        return "fa-code";
    }

    /**
     * Returns a map of paths to XSD files if the XML file should be validated, an empty map otherwise.
     * <p>
     * This allows to provide more than one XSD for validation when multiple variants of a data format are supported.
     *
     * @return a map of paths to XSD files if the XML file should be validated, an empty map otherwise
     */
    @Nonnull
    protected Map<String, String> getXsdResourcePaths() {
        return new HashMap<>();
    }
}
