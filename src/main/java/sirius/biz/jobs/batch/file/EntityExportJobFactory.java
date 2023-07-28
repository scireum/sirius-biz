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
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which export entities into line based files using a
 * {@link EntityExportJob}.
 *
 * @param <E> the type of entities being exported
 * @param <Q> the query type used to select entities if all are to be exported
 */
public abstract class EntityExportJobFactory<E extends BaseEntity<?>, Q extends Query<Q, E, ?>>
        extends LineBasedExportJobFactory {

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(EntityExportJob.TEMPLATE_FILE_PARAMETER);
    }

    @SuppressWarnings({"squid:S2095", "resource"})
    @Explain("The job must not be closed here as it is returned and managed by the caller.")
    @Override
    protected EntityExportJob<E, Q> createJob(ProcessContext process) {
        // We only resolve the parameters once and keep the final values around in a local context...
        ImportContext parameterContext = new ImportContext();
        transferParameters(parameterContext, process);

        return new EntityExportJob<E, Q>(getExportType(),
                                         getDictionary(),
                                         getDefaultMapping(),
                                         process,
                                         getName()).withQueryExtender(query -> extendSelectQuery(query, process))
                                                   .withEntityFilter(this::includeEntityDuringExport)
                                                   .withContextExtender(context -> context.putAll(parameterContext))
                                                   .withFileName(getCustomFileName());
    }

    /**
     * Permits to transfer parameters into the import context.
     * <p>
     * This is required as an export can process partial template files by looking up the matching entities and then
     * completing the rows.
     *
     * @param context        the context to enrich. This will be transferred to the underlying {@link Importer} and
     *                       {@link sirius.biz.importer.ImportHandler import handlers}
     * @param processContext the process context used to resolve parameter values
     */
    protected void transferParameters(ImportContext context, ProcessContext processContext) {
        // nothing to transfer by default
    }

    /**
     * Permits to return a custom file name when exporting the entity.
     * <p>
     * Otherwise, the "end-user friendly" plural of the entity is used as target file name
     */
    protected String getCustomFileName() {
        return null;
    }

    /**
     * Permits to add additional constraints on the query used to select all exportable entities.
     *
     * @param query          the query to extend
     * @param processContext the current process which can be used to extract parameters
     */
    protected void extendSelectQuery(Q query, ProcessContext processContext) {
        // Nothing to add by default
    }

    /**
     * Checks whether the given entity should be exported. This can be overridden to filter entities using more complex
     * logic than can be expressed by the query.
     * <p>
     * This method should be used with care.
     *
     * @param entity         the entity to check
     * @param processContext the current process which can be used to extract parameters
     * @return <tt>true</tt> if the entity should be exported, <tt>false</tt> otherwise
     */
    protected boolean includeEntityDuringExport(E entity, ProcessContext processContext) {
        return true;
    }

    /**
     * Creates the dictionary used by the export.
     *
     * @return the dictionary being used
     */
    protected ImportDictionary getDictionary() {
        try (Importer importer = new Importer("getDictionary")) {
            ImportDictionary dictionary = importer.getExportDictionary(getExportType());
            enhanceDictionary(dictionary);
            return dictionary;
        } catch (Exception e) {
            throw Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Defines the default list of export columns.
     *
     * @return the columns to export unless a custom list is given via a template file
     */
    protected List<String> getDefaultMapping() {
        try (Importer importer = new Importer("getDefaultMapping")) {
            return importer.findHandler(getExportType()).getDefaultExportMapping();
        } catch (Exception e) {
            throw Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Returns the main type being exported by this job.
     *
     * @return the type of entities being imported
     */
    protected abstract Class<E> getExportType();

    /**
     * Adds the possibility to enhance a dictionary during the setup of the job
     *
     * @param dictionary the dictionary to enhance
     */
    @SuppressWarnings("squid:S1186")
    @Explain("Do nothing by default since we only need this for exports which contain more than one Entity")
    protected void enhanceDictionary(ImportDictionary dictionary) {
    }

    @Override
    protected void collectJobInfos(JobInfoCollector collector) {
        super.collectJobInfos(collector);
        collector.addTranslatedCard("EntityExportJobFactory.templateModes");
        getDictionary().emitJobInfos(collector);
    }
}
