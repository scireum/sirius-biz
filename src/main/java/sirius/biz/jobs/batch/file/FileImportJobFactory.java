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
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.util.ArchiveExtractor;
import sirius.kernel.di.std.Part;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import files.
 */
public abstract class FileImportJobFactory extends ImportBatchProcessFactory {

    @Part
    protected ArchiveExtractor extractor;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel() + ": " + FileImportJob.FILE_PARAMETER.get(context).map(VirtualFile::name).orElse("-");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(createFileParameter());
        if (supportsAuxiliaryFiles()) {
            parameterCollector.accept(FileImportJob.AUX_FILE_MODE_PARAMETER);
        }
    }

    protected Parameter<VirtualFile> createFileParameter() {
        List<String> fileExtensions = new ArrayList<>();
        collectAcceptedFileExtensions(fileExtensions::add);
        fileExtensions.addAll(extractor.getSupportedFileExtensions());

        return FileImportJob.createFileParameter(fileExtensions);
    }

    protected abstract void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer);

    protected boolean supportsAuxiliaryFiles() {
        return false;
    }


}
