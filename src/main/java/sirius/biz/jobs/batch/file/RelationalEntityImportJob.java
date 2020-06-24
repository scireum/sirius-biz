/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.importer.txn.ImportTransactionHelper;
import sirius.biz.importer.txn.ImportTransactionalEntity;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.tenants.Tenants;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.data.LineBasedProcessor;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides a job for importing line based files (CSV, Excel) as relational entities.
 * <p>
 * This job behaves alomost exactly like {@link EntityImportJob}. The only difference is that it is suited for
 * "relational" entities (entities which represent a relation between two other entities). These are often
 * synchronized as described by {@link SyncMode}, which is handled by this implementation.
 * <p>
 * To support an efficient operation, such entities should implement {@link ImportTransactionalEntity} to that the
 * framework can provide efficient delta updates.
 *
 * @param <E> the type of entities being imported by this job
 * @param <Q> the generic type of queries for the entities being procesed
 */
public class RelationalEntityImportJob<E extends BaseEntity<?> & ImportTransactionalEntity, Q extends Query<Q, E, ?>>
        extends DictionaryBasedImportJob {

    @Part
    private static Mixing mixing;

    @Part
    private static Tenants<?, ?, ?> rawTenants;

    protected final EntityDescriptor descriptor;
    protected Consumer<Context> contextExtender;
    protected ImportTransactionHelper importTransactionHelper;
    protected Class<E> type;
    protected SyncMode mode;
    protected BiConsumer<ProcessContext, Q> queryTuner;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param fileParameter        the parameter which is used to derive the import file from
     * @param ignoreEmptyParameter the parameter which is used to determine if empty values should be ignored
     * @param syncModeParameter    the parameter which is used to determine the {@link SyncMode} to use
     * @param type                 the type of entities being imported
     * @param dictionary           the import dictionary to use
     * @param process              the process context itself
     * @param factoryName          the name of the factory which created this job
     */
    public RelationalEntityImportJob(FileParameter fileParameter,
                                     BooleanParameter ignoreEmptyParameter,
                                     EnumParameter<SyncMode> syncModeParameter,
                                     Class<E> type,
                                     ImportDictionary dictionary,
                                     ProcessContext process,
                                     String factoryName) {
        super(fileParameter, ignoreEmptyParameter, dictionary, process);
        this.importer.setFactoryName(factoryName);
        this.importTransactionHelper = importer.findHelper(ImportTransactionHelper.class);
        this.mode = process.getParameter(syncModeParameter).orElse(SyncMode.NEW_AND_UPDATE_ONLY);
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
    }

    /**
     * Specifies a context extender which can be used to transfer job parameters into the import context.
     *
     * @param contextExtender the extender to specify
     * @return the import job itself for fluent method calls
     */
    public RelationalEntityImportJob<E, Q> withContextExtender(Consumer<Context> contextExtender) {
        this.contextExtender = contextExtender;
        return this;
    }

    /**
     * Specifies the delete query tuner to use.
     * <p>
     * This permits to control which enities will be deleted if the remain unmarked during an import.
     *
     * @param queryTuner the tuner to invoke
     * @return the job itself for fluent method calls
     */
    public RelationalEntityImportJob<E, Q> withDeleteQueryTuner(BiConsumer<ProcessContext, Q> queryTuner) {
        this.queryTuner = queryTuner;
        return this;
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        importTransactionHelper.start();
        LineBasedProcessor.create(filename, in).run(this, error -> {
            process.handle(error);
            return true;
        });
        commitImportTransaction();
    }

    /**
     * Commits the import transaction by deleting all untouched entities.
     */
    @SuppressWarnings("unchecked")
    protected void commitImportTransaction() {
        if (mode != SyncMode.SYNC) {
            return;
        }

        Watch watch = Watch.start();
        importTransactionHelper.deleteUnmarked(type, query -> tuneImportTransactionDeleteQuery((Q) query), entity -> {
            process.addTiming(NLS.get("EntityImportJob.entityDeleted"), watch.elapsed(TimeUnit.MILLISECONDS, true));
        });
    }

    /**
     * Tunes the delete query of the import transaction so that all untouched entities will be deleted.
     *
     * @param deleteQuery the query to enhance
     */
    protected void tuneImportTransactionDeleteQuery(Q deleteQuery) {
        queryTuner.accept(process, deleteQuery);
    }

    @Override
    protected final void handleRow(int index, Context context) {
        Watch watch = Watch.start();

        if (contextExtender != null) {
            contextExtender.accept(context);
        }

        E entity = findAndLoad(context);
        if (mode == SyncMode.DELETE_EXISTING) {
            if (!entity.isNew()) {
                importer.deleteNow(entity);
                process.addTiming(NLS.get("EntityImportJob.entityDeleted"), watch.elapsedMillis());
            }
        } else {
            createOrUpdateEntity(entity, context, watch);
        }
    }

    protected void createOrUpdateEntity(E entity, Context context, Watch watch) {
        try {
            if (shouldSkip(entity)) {
                process.incCounter(NLS.get("EntityImportJob.rowIgnored"));
                return;
            }

            fillAndVerify(entity, context);
            if (entity.isNew()) {
                process.addTiming(NLS.get("EntityImportJob.entityCreated"), watch.elapsedMillis());
            } else {
                process.addTiming(NLS.get("EntityImportJob.entityUpdated"), watch.elapsedMillis());
            }
        } catch (HandledException e) {
            throw Exceptions.createHandled()
                            .withNLSKey("EntityImportJob.cannotHandleEntity")
                            .set("entity", entity.toString())
                            .set("message", e.getMessage())
                            .handle();
        }
    }

    /**
     * Tries to resolve the context into an entity.
     * <p>
     * Overwrite this method do add additional parameters to the <tt>context</tt>.
     *
     * @param context the context containing all relevant data
     * @return the entity which was either found in he database or create using the given data
     */
    protected E findAndLoad(Context context) {
        return importer.findAndLoad(type, context);
    }

    /**
     * Determines if the given entity should be persisted or skipped.
     *
     * @param entity the entity to check
     * @return <tt>true</tt> if further processing should be skipped, <tt>false</tt> if the entity should be persisted
     */
    protected boolean shouldSkip(E entity) {
        return mode == SyncMode.NEW_ONLY && !entity.isNew() || (mode == SyncMode.UPDATE_ONLY && entity.isNew());
    }

    /**
     * Completes the given entity and verifies the integrity of the data.
     * <p>
     * This method is intended to be overwritten but note that most of the consistency checks should be performed
     * in the {@link sirius.biz.importer.ImportHandler} itself if possible.
     *
     * @param entity  the entity which has be loaded previously
     * @param context the row represented as context
     * @return the filled and verified entity
     */
    protected E fillAndVerify(E entity, Context context) {
        return entity;
    }

    /**
     * Creates or updates the given entity.
     * <p>
     * This can be overwritten to use a custom way of persisting data. Also this can be used to perfrom
     * post-save activities.
     * <p>
     * By default we instantly create or update the entity. Note that if this is set to batch updates,
     * a post-save handler would need to be a
     * {@link sirius.biz.importer.ImporterContext#addPostCommitCallback(Runnable)}.
     *
     * @param entity  the entity to persist
     * @param context the row represented as context
     * @see sirius.biz.importer.Importer#createOrUpdateNow(BaseEntity)
     */
    protected void createOrUpdate(E entity, Context context) {
        importer.createOrUpdateNow(entity);
    }
}
