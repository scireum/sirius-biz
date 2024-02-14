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

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import XML files using a {@link XMLExportJob}.
 */
public abstract class XMLExportJobFactory extends FileExportJobFactory {

    protected static final Parameter<Boolean> VALID_FILE_PARAMETER =
            new BooleanParameter("requireValidFile", "$XMLExportJobFactory.requireValidFile").withDescription(
                    "$XMLExportJobFactory.requireValidFile.help").build();

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        if (getXsdResourcePath() != null) {
            parameterCollector.accept(VALID_FILE_PARAMETER);
        }
        super.collectParameters(parameterCollector);
    }

    @Override
    protected void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer) {
        fileExtensionConsumer.accept("zip");
    }

    @Override
    protected abstract XMLExportJob createJob(ProcessContext process);

    @Override
    public String getIcon() {
        return "fa-solid fa-code";
    }

    /**
     * Returns the path to the XSD file if the XML file should be validated, null otherwise.
     *
     * @return the path to the XSD file if the XML file should be validated, null otherwise
     */
    @Nullable
    protected String getXsdResourcePath() {
        return null;
    }
}
