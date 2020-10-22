/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.ExportBatchProcessFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.VirtualFile;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which export data into a file.
 */
public abstract class FileExportJobFactory extends ExportBatchProcessFactory {

    protected FileOrDirectoryParameter destinationParameter = createDestinationParameter();

    /**
     * Creates the parameter which is used to specify the destination for the generated output file.
     * <p>
     * This is provided as a helper method so that other / similar jobs can re-use it.
     * We do not re-use the same parameter, as a parameter isn't immutable, so a global constant could
     * be easily set into an inconsistent state.
     *
     * @return the completely initialized parameter.
     */
    public static FileOrDirectoryParameter createDestinationParameter() {
        return new FileOrDirectoryParameter("destination", "$FileExportJobFactory.destination").withDescription(
                "$FileExportJobFactory.destination.help").withBasePath("/work");
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + destinationParameter.get(context)
                                                .filter(VirtualFile::isFile)
                                                .map(VirtualFile::toString)
                                                .map(filename -> ": " + filename)
                                                .orElse("");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(destinationParameter);
    }

    @Override
    protected abstract FileExportJob createJob(ProcessContext process);
}
