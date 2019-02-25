/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.db.mixing.Mapping;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import java.util.Optional;

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

    @SuppressWarnings("unchecked")
    @Override
    public Optional<E> tryFindInCache(Context data) {
        Tuple<Class<?>, String> cacheKey = Tuple.create(descriptor.getType(), determineCacheKey(data));
        if (Strings.isFilled(cacheKey.getSecond())) {
            Object result = context.getLocalCache().getIfPresent(cacheKey);
            if (result != null) {
                return Optional.of((E) result);
            }
        }

        Optional<E> result = tryFind(data);
        if (result.isPresent() && Strings.isFilled(cacheKey)) {
            context.getLocalCache().put(cacheKey, result.get());
        }

        return result;
    }

    /**
     * Determines the cache key used by {@link #tryFindInCache(Context)} to find an instance in the cache.
     * <p>
     * Note that this isn't implemented by default and has to be overwritten by subclasses which want to support caching.
     *
     * @param data the data used to determine the cache key from
     * @return a unique string representation used for cache lookups or <tt>null</tt> to indicate that either caching
     * is completely disabled or that the example instance doesn't provide values in the relevant fields to support a
     * cache lookup.
     */
    protected String determineCacheKey(Context data) {
        throw new UnsupportedOperationException();
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
