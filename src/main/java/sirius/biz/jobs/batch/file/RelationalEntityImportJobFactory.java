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
import sirius.biz.importer.txn.ImportTransactionalEntity;
import sirius.biz.jobs.infos.JobInfoCollector;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which synchronize a set of entities based on line-based files using a
 * {@link sirius.biz.importer.ImportHandler}.
 *
 * @param <E> the type of entities being imported by this job
 * @param <Q> the generic type of queries for the entities being processed
 */
public abstract class RelationalEntityImportJobFactory<E extends BaseEntity<?> & ImportTransactionalEntity, Q extends Query<Q, E, ?>>
        extends DictionaryBasedImportJobFactory {

    @Override
    protected DictionaryBasedImportJob createJob(ProcessContext process) {
        // We only resolve the parameters once and keep the final values around in a local context...
        ImportContext parameterContext = new ImportContext();
        transferParameters(parameterContext, process);

        return createImportJob(process, parameterContext);
    }

    @SuppressWarnings("squid:S2095")
    @Explain("The job must not be closed here as it is returned and managed by the caller.")
    protected RelationalEntityImportJob<E, Q> createImportJob(ProcessContext process, ImportContext parameterContext) {
        return new RelationalEntityImportJob<E, Q>(getImportType(),
                                                   getDictionary(),
                                                   process,
                                                   getName()).withDeleteQueryTuner(this::tuneDeleteQuery)
                                                             .withContextExtender(context -> context.putAll(
                                                                     parameterContext));
    }

    protected void tuneDeleteQuery(ProcessContext processContext, Q query) {
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
    protected abstract Class<E> getImportType();

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
        collector.addTranslatedWell("EntityImportJobFactory.automaticMappings");
        getDictionary().emitJobInfos(collector);
    }

    /**
     * Defines if the {@link RelationalEntityImportJob#SYNC_SOURCE_PARAMETER source} parameter should be included.
     * <p>
     * This parameter controls which records will be deleted when {@link RelationalEntityImportJob#SYNC_MODE_PARAMETER}
     * is set to {@link SyncMode#SYNC}.
     * By default, no source is collected and all records will be deleted, which were not
     * {@link sirius.biz.importer.txn.ImportTransactionHelper#mark(ImportTransactionalEntity) marked}.
     *
     * @return <tt>true</tt> to enable the source parameter, <tt>false</tt> otherwise.
     */
    protected boolean enableSyncSourceParameter() {
        return false;
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(RelationalEntityImportJob.SYNC_MODE_PARAMETER);
        if (enableSyncSourceParameter()) {
            parameterCollector.accept(RelationalEntityImportJob.SYNC_SOURCE_PARAMETER);
        }
    }
}
