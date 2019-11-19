/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

/**
 * Provides a job for importing line based files (CSV, Excel) as entities.
 * <p>
 * Utilizing {@link sirius.biz.importer.ImportHandler import handlers} this can be used as is in most cases. However
 * a subclass overwriting {@link #handleRow(int, Context)} might be required to perform some mappings.
 *
 * @param <E> the type of entities being imported by this job
 */
public class EntityImportJob<E extends BaseEntity<?>> extends DictionaryBasedImportJob {

    protected final EntityDescriptor descriptor;
    protected Class<E> type;
    protected ImportMode mode;

    @Part
    private static Mixing mixing;

    @Part
    private static Tenants<?, ?, ?> rawTenants;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param fileParameter        the parameter which is used to derive the import file from
     * @param ignoreEmptyParameter the parameter which is used to determine if empty values should be ignored
     * @param importModeParameter  the parameter which is used to determine the {@link ImportMode} to use
     * @param type                 the type of entities being imported
     * @param dictionary           the import dictionary to use
     * @param process              the process context itself
     */
    public EntityImportJob(FileParameter fileParameter,
                           BooleanParameter ignoreEmptyParameter,
                           EnumParameter<ImportMode> importModeParameter,
                           Class<E> type,
                           ImportDictionary dictionary,
                           ProcessContext process) {
        super(fileParameter, ignoreEmptyParameter, dictionary, process);
        this.mode = process.getParameter(importModeParameter).orElse(ImportMode.NEW_AND_UPDATES);
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
    }

    @Override
    protected void backupInputFile(VirtualFile input) {
        // No need to create a backup copy if we only run a check...
        if (mode != ImportMode.CHECK_ONLY) {
            super.backupInputFile(input);
        }
    }

    @Override
    protected void handleRow(int index, Context context) {
        Watch watch = Watch.start();
        E entity = findAndLoad(context);
        try {
            if (shouldSkip(entity)) {
                process.addCounter(NLS.get("EntityImportJob.rowIgnored"));
                return;
            }

            fillAndVerify(entity);
            if (mode == ImportMode.CHECK_ONLY) {
                entity.getDescriptor().beforeSave(entity);
            } else {
                importer.createOrUpdateInBatch(entity);
            }

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

    private boolean shouldSkip(E entity) {
        return mode == ImportMode.NEW_ONLY && !entity.isNew() || (mode == ImportMode.UPDATE_ONLY && entity.isNew());
    }

    /**
     * Completes the given entity and verifies the integrity of the data.
     *
     * @param entity the entity which has be loaded previously
     * @return the filled and verified entity
     */
    protected E fillAndVerify(E entity) {
        if (entity instanceof TenantAware) {
            if (entity.isNew()) {
                ((TenantAware) entity).fillWithCurrentTenant();
            } else {
                rawTenants.assertTenant((TenantAware) entity);
            }
        }

        return entity;
    }

    /**
     * Tries to resolve the context into an entity.
     *
     * @param context the context containing all relevant data
     * @return the entity which was either found in he database or create using the given data
     */
    protected E findAndLoad(Context context) {
        return importer.findAndLoad(type, context);
    }
}
