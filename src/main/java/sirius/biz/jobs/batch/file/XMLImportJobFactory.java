/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import XML files using a {@link XMLImportJob}.
 */
public abstract class XMLImportJobFactory extends FileImportJobFactory {

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);

        Map<String, String> paths = new LinkedHashMap<>();
        collectXsdResourcePaths(paths::put);
        if (!paths.isEmpty()) {
            parameterCollector.accept(XMLImportJob.createSchemaParameter(paths));
        }
    }

    @Override
    protected void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer) {
        fileExtensionConsumer.accept("xml");
    }

    @Override
    protected abstract XMLImportJob createJob(ProcessContext process);

    @Override
    public String getIcon() {
        return "fa-solid fa-code";
    }

    /**
     * Adds xsd schema paths to the entryConsumer if the XML file should and can be validated.
     * <p>
     * This allows to provide more than one XSD for validation when multiple variants of a data format are supported.
     */
    protected void collectXsdResourcePaths(BiConsumer<String, String> entryConsumer) {
        // Nothing to do in the default implementation
    }
}
