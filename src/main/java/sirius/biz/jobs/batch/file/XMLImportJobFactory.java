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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
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
        Map<String, String> paths = new LinkedHashMap<>();
        fillXsdResourcePaths(paths::put);
        if (!paths.isEmpty()) {
            paths.forEach((xsdPath, name) -> requireValidFile.withEntry(xsdPath, name));
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
     * Adds xsd schema paths to the entryConsumer if the XML file should and can be validated.
     * <p>
     * This allows to provide more than one XSD for validation when multiple variants of a data format are supported.
     */
    protected void fillXsdResourcePaths(BiConsumer<String, String> entryConsumer) {
        // Nothing to do in the default implementation
    }
}
