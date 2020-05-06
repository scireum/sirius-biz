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
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a
 * {@link sirius.biz.importer.ImportHandler}.
 */
public abstract class EntityImportJobFactory extends DictionaryBasedImportJobFactory {

    /**
     * Determines the {@link ImportMode}.
     */
    protected final EnumParameter<ImportMode> importModeParameter = createImportModeParameter();

    /**
     * Creates the parameter which determines the import mode to use.
     * <p>
     * This is provided as a helper method so that other / similar jobs can re-use it.
     * We do not re-use the same parameter, as a parameter isn't immutable, so a global constant could
     * be easily set into an inconsistent state.
     *
     * @return the completely initialized parameter.
     */
    public static EnumParameter<ImportMode> createImportModeParameter() {
        EnumParameter<ImportMode> importModeParameter =
                new EnumParameter<>("importMode", "$EntityImportJobFactory.importMode", ImportMode.class);
        importModeParameter.withDefault(ImportMode.NEW_AND_UPDATES);
        importModeParameter.markRequired();
        importModeParameter.withDescription("$EntityImportJobFactory.importMode.help");
        return importModeParameter;
    }

    protected EntityImportJobFactory() {
        // Makes the constructor protected as this is an abstract class
    }

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
        return new EntityImportJob<>(fileParameter,
                                     ignoreEmptyParameter,
                                     importModeParameter,
                                     getImportType(),
                                     getDictionary(),
                                     process,
                                     getName()).withContextExtender(context -> context.putAll(parameterContext));
    }

    /**
     * Permits to transfer parameters into the import context.
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
        } catch (Exception e) {
            throw Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Returns the main type being imported by this job.
     *
     * @return the type of entities being imported
     */
    protected abstract Class<? extends BaseEntity<?>> getImportType();

    /**
     * Adds the possibility to enhance a dicitonary during the setup of the job
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
        collector.addTranslatedWell("EntityImportJobFactory.automaticMappings");
        getDictionary().emitJobInfos(collector);
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(importModeParameter);
    }
}
