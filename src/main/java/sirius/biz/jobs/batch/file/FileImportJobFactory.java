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
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.VirtualFile;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import files.
 */
public abstract class FileImportJobFactory extends ImportBatchProcessFactory {

    /**
     * Contains the parameter which is used to select the file (as <tt>VirtualFile</tt>).
     */
    protected final FileParameter fileParameter = createInputFileParameter();

    /**
     * Creates the parameter which is used to specify the input file.
     * <p>
     * This is provided as a helper method so that other / similar jobs can re-use it.
     * We do not re-use the same parameter, as a parameter isn't immutable, so a global constant could
     * be easily set into an inconsistent state.
     *
     * @return the completely initialized parameter.
     */
    public static FileParameter createInputFileParameter() {
        return new FileParameter("file", "$FileImportJobFactory.file").withBasePath("/work").markRequired();
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + fileParameter.get(context).map(VirtualFile::name).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(fileParameter);
    }
}
