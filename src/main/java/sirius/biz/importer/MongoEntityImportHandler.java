/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Provides a base implementation for all {@link MongoEntity MongoDB entities}.
 *
 * @param <E> the type of entity being handled by this handler
 */
public abstract class MongoEntityImportHandler<E extends MongoEntity> extends BaseImportHandler<E> {

    protected static final Mapping[] MAPPING_ARRAY = new Mapping[0];

    @Part
    protected Mango mango;

    protected Mapping[] mappingsToLoad;

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected MongoEntityImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
        this.mappingsToLoad = getAutoImportMappings().toArray(MAPPING_ARRAY);
    }

    @Override
    public E load(Context data, E entity) {
        return load(data, entity, mappingsToLoad);
    }

    @Override
    protected E load(Context data, E entity, Mapping... mappings) {
        E e = super.load(data, entity, mappings);
        if (e instanceof MongoTenantAware) {
            ((MongoTenantAware) e).fillWithCurrentTenant();
        }

        return e;
    }

    @Override
    protected void collectExportableMappings(BiConsumer<Integer, Mapping> collector) {
        // Empty by default as this is kind of an exotic way to extend the handler
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(10, SQLEntity.ID);
    }

    @Override
    public E createOrUpdateNow(E entity) {
        mango.update(entity);
        return entity;
    }

    @Override
    public void createOrUpdateInBatch(E entity) {
        createOrUpdateNow(entity);
    }

    @Override
    public void createNowOrUpdateInBatch(E entity) {
        createOrUpdateNow(entity);
    }

    @Override
    public void deleteNow(E entity) {
        mango.delete(entity);
    }

    @Override
    public void deleteInBatch(E entity) {
        deleteNow(entity);
    }

    @Override
    public void commit() {
        // Empty as we currently have no support for batch inserts or updates...
    }
}
