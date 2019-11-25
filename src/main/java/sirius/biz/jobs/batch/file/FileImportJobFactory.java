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
    protected final FileParameter fileParameter =
            new FileParameter("file", "$FileImportJobFactory.file").withBasePath("/work").markRequired();

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + fileParameter.get(context).map(VirtualFile::name).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(fileParameter);
    }
}
