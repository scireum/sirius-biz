/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.data.ExcelExport;
import sirius.web.http.WebContext;

import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a
 * {@link sirius.biz.importer.format.ImportDictionary}.
 */
public abstract class DictionaryBasedImportJobFactory extends LineBasedImportJobFactory {

    @Override
    protected abstract DictionaryBasedImportJob createJob(ProcessContext process);

    /**
     * Creates the dictionary used by the import.
     *
     * @return the dictionary being used
     */
    protected abstract ImportDictionary getDictionary();

    /**
     * Indicates whether a template download is supported for this job.
     *
     * @return true if a template can be downloaded, false otherwise
     */
    @Override
    public boolean canDownloadTemplate() {
        return true;
    }

    /**
     * Responds with a template for this import job.
     *
     * @param webContext the web context for the response
     */
    @Override
    public void respondWithTemplate(WebContext webContext) {
        try {
            OutputStream out = webContext.respondWith()
                                         .download("template-" + getName() + ".xlsx")
                                         .outputStream(HttpResponseStatus.OK, null);
            ExcelExport export = ExcelExport.asStandardXLSX();

            Object[] headers = getDictionary().getFields()
                                              .stream()
                                              .filter(field -> !field.isHidden())
                                              .map(FieldDefinition::getLabel)
                                              .toArray();
            export.addArrayRow(headers);
            export.writeToStream(out);
        } catch (Exception exception) {
            throw Exceptions.handle(Log.BACKGROUND, exception);
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(DictionaryBasedImport.IGNORE_EMPTY_PARAMETER);
    }
}
