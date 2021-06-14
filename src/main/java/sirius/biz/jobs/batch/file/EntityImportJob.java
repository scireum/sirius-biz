/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.tenants.Tenants;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a job for importing line based files (CSV, Excel) as entities.
 * <p>
 * Utilizing {@link sirius.biz.importer.ImportHandler import handlers} this can be used as is in most cases.
 * <p>
 * Note that {@link #enforceSaveConstraints(BaseEntity, Context)} can be overwritten to perform any pre-save activities or
 * {@link #createOrUpdate(BaseEntity, Context)} to use a custom way to persist data or to perform some post-save activities.
 *
 * @param <E> the type of entities being imported by this job
 */
public class EntityImportJob<E extends BaseEntity<?>> extends DictionaryBasedImportJob {

    /**
     * Contains the parameter this is used to select the import mode.
     */
    public static final Parameter<ImportMode> IMPORT_MODE_PARAMETER =
            new EnumParameter<>("importMode", "$EntityImportJobFactory.importMode", ImportMode.class).withDefault(
                    ImportMode.NEW_AND_UPDATES)
                                                                                                     .markRequired()
                                                                                                     .withDescription(
                                                                                                             "$EntityImportJobFactory.importMode.help")
                                                                                                     .build();

    @Part
    private static Mixing mixing;

    @Part
    @Nullable
    private static Tenants<?, ?, ?> rawTenants;

    protected final EntityDescriptor descriptor;
    protected Consumer<Context> contextExtender;
    protected Class<E> type;
    protected ImportMode mode;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param type        the type of entities being imported
     * @param dictionary  the import dictionary to use
     * @param process     the process context itself
     * @param factoryName the name of the factory which created this job
     */
    public EntityImportJob(Class<E> type, ImportDictionary dictionary, ProcessContext process, String factoryName) {
        super(dictionary, process);
        importer.setFactoryName(factoryName);
        this.mode = process.getParameter(IMPORT_MODE_PARAMETER).orElse(ImportMode.NEW_AND_UPDATES);
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
    }

    /**
     * Specifies a context extender which can be used to transfer job parameters into the import context.
     *
     * @param contextExtender the extender to specify
     * @return the import job itself for fluent method calls
     */
    public EntityImportJob<E> withContextExtender(Consumer<Context> contextExtender) {
        this.contextExtender = contextExtender;
        return this;
    }

    @Override
    protected void backupInputFile(String name, FileHandle input) {
        // No need to create a backup copy if we only run a check...
        if (mode != ImportMode.CHECK_ONLY) {
            super.backupInputFile(name, input);
        }
    }

    @Override
    protected final void handleRow(int index, Context context) {
        Watch watch = Watch.start();

        if (contextExtender != null) {
            contextExtender.accept(context);
        }

        E entity = findAndLoad(context);
        try {
            if (shouldSkip(entity)) {
                process.incCounter(NLS.get("EntityImportJob.rowIgnored"));
                return;
            }

            fillAndVerify(entity, context);
            boolean isNew = entity.isNew();

            if (mode == ImportMode.CHECK_ONLY) {
                enforceSaveConstraints(entity, context);
            } else {
                createOrUpdate(entity, context);
            }

            if (isNew) {
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
     * Enforces the save constraints manually in case of {@link ImportMode#CHECK_ONLY}.
     *
     * @param entity  the entity to check
     * @param context the row represented as context
     */
    protected void enforceSaveConstraints(E entity, Context context) {
        importer.findHandler(type).enforcePreSaveConstraints(entity);
        entity.getDescriptor().beforeSave(entity);
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

    protected boolean shouldSkip(E entity) {
        return mode == ImportMode.NEW_ONLY && !entity.isNew() || (mode == ImportMode.UPDATE_ONLY && entity.isNew());
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
