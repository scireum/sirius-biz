/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.ImportContext;
import sirius.biz.importer.Importer;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.infos.JobInfoCollector;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import sirius.biz.importer.format.FieldDefinition;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a
 * {@link sirius.biz.importer.ImportHandler}.
 */
public abstract class EntityImportJobFactory extends DictionaryBasedImportJobFactory {

    @Override
    protected DictionaryBasedImportJob createJob(ProcessContext process) {
        // We only resolve the parameters once and keep the final values around in a local context...
        ImportContext parameterContext = new ImportContext();
        transferParameters(parameterContext, process);

        return createImportJob(process, parameterContext);
    }

    @SuppressWarnings("squid:S2095")
    @Explain("The job must not be closed here as it is returned and managed by the caller.")
    protected DictionaryBasedImportJob createImportJob(ProcessContext process, ImportContext parameterContext) {
        return new EntityImportJob<>(getImportType(),
                                     getDictionary(),
                                     process,
                                     getName()).withContextExtender(context -> context.putAll(parameterContext));
    }

    /**
     * Permits transferring parameters into the import context.
     *
     * @param context        the context to enrich. This will be transferred to the underlying {@link Importer} and
     *                       {@link sirius.biz.importer.ImportHandler import handlers}
     * @param processContext the process context used to resolve parameter values
     */
    protected void transferParameters(ImportContext context, ProcessContext processContext) {
        // nothing to transfer by default
    }

    /**
     * Creates the dictionary used by the import.
     *
     * @return the dictionary being used
     */
    protected ImportDictionary getDictionary() {
        try (Importer importer = new Importer("getDictionary")) {
            ImportDictionary dictionary = importer.getImportDictionary(getImportType());
            enhanceDictionary(importer, dictionary);
            return dictionary;
        } catch (Exception exception) {
            throw Exceptions.handle(Log.BACKGROUND, exception);
        }
    }

    /**
     * Returns the standard version of the {@link EntityImportJob#IMPORT_MODE_PARAMETER}.
     * <p>
     * Override this method if this parameter must be customized for specific imports.
     *
     * @return the parameter used to control {@link ImportMode import modes}
     */
    protected Parameter<ImportMode> createImportModeParameter() {
        return EntityImportJob.IMPORT_MODE_PARAMETER;
    }

    /**
     * Returns the main type being imported by this job.
     *
     * @return the type of entities being imported
     */
    protected abstract Class<? extends BaseEntity<?>> getImportType();

    /**
     * Adds the possibility to enhance a dictionary during the setup of the job
     *
     * @param importer   the current importer which can be asked to provide a dictionary for an entity
     * @param dictionary the dictionary to enhance
     */
    @SuppressWarnings("squid:S1186")
    @Explain("Do nothing by default since we only need this for imports which contain more than one Entity")
    protected void enhanceDictionary(Importer importer, ImportDictionary dictionary) {
    }

    @Override
    protected void collectJobInfos(JobInfoCollector collector) {
        super.collectJobInfos(collector);
        collector.addTranslatedCard("EntityImportJobFactory.automaticMappings");
        getDictionary().emitJobInfos(collector);
    }

    @Override
    @Nullable
    public String generateTemplateUrl() {
        try {
            // Collect all visible field labels from the dictionary and join them with a semicolon
            String headers = getDictionary().getFields().stream()
                                            .filter(field -> !field.isHidden())
                                            .map(FieldDefinition::getLabel)
                                            .collect(Collectors.joining(";"));

            // Prepare the CSV content and add the UTF-8 Byte Order Mark (BOM) for Excel compatibility
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] csvBytes = headers.getBytes(StandardCharsets.UTF_8);
            byte[] fullContent = new byte[bom.length + csvBytes.length];

            System.arraycopy(bom, 0, fullContent, 0, bom.length);
            System.arraycopy(csvBytes, 0, fullContent, bom.length, csvBytes.length);

            // Convert the combined byte array to a Base64 string and return it as a Data-URI
            return "data:text/csv;base64," + Base64.getEncoder().encodeToString(fullContent);
        } catch (Exception e) {
            Exceptions.handle(Log.BACKGROUND, e);
            return null;
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(createImportModeParameter());
    }
}
