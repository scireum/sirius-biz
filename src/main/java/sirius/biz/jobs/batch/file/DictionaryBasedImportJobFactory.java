/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import org.apache.commons.io.output.ByteArrayOutputStream;
import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.kernel.commons.CSVWriter;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nullable;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Override
    @Nullable
    public String generateTemplateUrl() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeUnicodeBOM();
            Object[] headers = getDictionary().getFields()
                                              .stream()
                                              .filter(field -> !field.isHidden())
                                              .map(FieldDefinition::getLabel)
                                              .toArray();
            csvWriter.writeArray(headers);
            writer.flush();
            return "data:text/csv;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception exception) {
            Exceptions.handle(Log.BACKGROUND, exception);
            return null;
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(DictionaryBasedImport.IGNORE_EMPTY_PARAMETER);
    }
}
