/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.batch.FindQuery;
import sirius.db.jdbc.batch.InsertQuery;
import sirius.db.jdbc.batch.UpdateQuery;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provides a base implementation and also generic handler for all {@link SQLEntity JDBC/SQL entities}.
 * <p>
 * Subclasses might most probably want to overwrite: {@link #getMappingsToFind()}, {@link #getMappingsToLoad()} and
 * maybe {@link #tryFindByExample(SQLEntity)} if a more complex lookup is necessary.
 *
 * @param <E> the type of entity being handled by this handler
 */
public class SQLEntityImportHandler<E extends SQLEntity> extends BaseImportHandler<E> {

    /**
     * Makes the {@link SQLEntityImportHandler} available as "last resort" for all {@link SQLEntity JDBC/SQL entities}.
     */
    @Register
    public static class SQLEntityImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public int getPriority() {
            return 999;
        }

        @Override
        public boolean accepts(Class<?> type) {
            return SQLEntity.class.isAssignableFrom(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ImportHandler<?> create(Class<?> type, ImportContext context) {
            return new SQLEntityImportHandler<>(type, context);
        }
    }

    protected static final Mapping[] MAPPING_ARRAY = new Mapping[0];

    protected FindQuery<E> findQuery;
    protected UpdateQuery<E> updateQuery;
    protected InsertQuery<E> insertQuery;

    protected Mapping[] mappingsToLoad;
    protected Mapping[] mappingsToFind;
    protected Mapping[] mappingsToCompare;

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected SQLEntityImportHandler(Class<?> clazz, ImportContext context) {
        super(clazz, context);
        this.mappingsToLoad = getMappingsToLoad().toArray(MAPPING_ARRAY);
        this.mappingsToFind = getMappingsToFind().toArray(MAPPING_ARRAY);
        this.mappingsToCompare = getMappingsToCompare().toArray(MAPPING_ARRAY);
    }

    /**
     * Returns a list of mappings to use (compare) if an entity is being looked up.
     *
     * @return a list of mappings used by <tt>find</tt>
     */
    protected List<Mapping> getMappingsToFind() {
        return Collections.singletonList(SQLEntity.ID);
    }

    /**
     * Returns a list of mappings to use (compare) if an entity is being updated.
     *
     * @return a list of mappings used in the <tt>WHERE</tt> part of <tt>INSERT</tt>
     */
    protected List<Mapping> getMappingsToCompare() {
        return Collections.singletonList(SQLEntity.ID);
    }

    /**
     * Returns a list of mappings to load for <tt>INSERT</tt> or <tt>UPDATE</tt>
     *
     * @return the list of mappings to update in this entity
     */
    protected List<Mapping> getMappingsToLoad() {
        return descriptor.getProperties()
                         .stream()
                         .map(Property::getName)
                         .filter(Predicate.isEqual(SQLEntity.ID.getName()).negate())
                         .map(Mapping::named)
                         .collect(Collectors.toList());
    }

    @Override
    public E load(Context data) {
        return load(data, mappingsToLoad);
    }

    @Override
    public Optional<E> tryFind(Context data) {
        return tryFindByExample(load(data, mappingsToFind));
    }

    /**
     * Tries to find a persistent entity using the given example.
     *
     * @param example the example instance used to search by
     * @return a matching entity wrapped as optional or an empty optional if no match is available
     */
    protected Optional<E> tryFindByExample(E example) {
        return getFindQuery().find(example);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<E> tryFindInCache(Context data) {
        E example = load(data, mappingsToFind);
        Tuple<Class<?>, String> cacheKey = Tuple.create(descriptor.getType(), determineCacheKey(example));
        if (Strings.isFilled(cacheKey.getSecond())) {
            Object result = context.getLocalCache().getIfPresent(cacheKey);
            if (result != null) {
                return Optional.of((E) result);
            }
        }

        Optional<E> result = tryFindByExample(load(data, mappingsToFind));
        if (result.isPresent() && Strings.isFilled(cacheKey)) {
            context.getLocalCache().put(cacheKey, result.get());
        }

        return result;
    }

    /**
     * Determines the cache key used by {@link #tryFindInCache(Context)} to find an instance in the cache.
     *
     * @param example the entity to derive the cache key from
     * @return a unique string representation used for cache lookups or <tt>null</tt> to indicate that either caching
     * is completely disabled or that the example instance doesn't provide values in the relevant fields to support a
     * cache lookup.
     */
    protected String determineCacheKey(E example) {
        return example.isNew() ? null : example.getIdAsString();
    }

    @Override
    public E createOrUpdateNow(E entity) {
        return createOrUpdate(entity, false);
    }

    @Override
    public void createOrUpdateInBatch(E entity) {
        createOrUpdate(entity, true);
    }

    /**
     * Determines if and how the given entity needs to be persisted.
     * <p>
     * If the given entity was already persisted, an update is pushed to the database. If the entity is
     * {@link BaseEntity#isNew() new}, we try to {@link #tryFindByExample(SQLEntity) find} a persistent version and
     * update this entity. If no match was found, an insert is performed.
     *
     * @param entity the entity to update or create
     * @param batch  <tt>true</tt> if batch updates /creates should be used, <tt>false</tt> otherwise
     * @return the updated or created entity or, if batch updates are active, the given entity
     */
    protected E createOrUpdate(E entity, boolean batch) {
        E entityToUpdate = entity;
        if (entity.isNew()) {
            entityToUpdate = tryFindByExample(entity).orElse(null);
            if (entityToUpdate == null) {
                return createIfChanged(entity, batch);
            }

            updatePersistentEntity(entity, entityToUpdate);
        }

        return updateIfChanged(entityToUpdate, batch);
    }

    /**
     * Creates a new entity into the database if there are any values set at all.
     *
     * @param entity the entity to insert into the database
     * @param batch  <tt>true</tt> if batch inserts should be used, <tt>false</tt> otherwise
     * @return the created entity or, if batch updates are active, the given entity
     */
    protected E createIfChanged(E entity, boolean batch) {
        if (entity.isChanged(mappingsToLoad)) {
            getInsertQuery().insert(entity, true, batch);
        }

        return entity;
    }

    /**
     * Transfers all changed properties from the updated entity to the entity loaded from the database.
     *
     * @param entityWithUpdates the entity which contains the update
     * @param persistentEntity  the entity which was loaded from the database and is to be updated
     */
    protected void updatePersistentEntity(E entityWithUpdates, E persistentEntity) {
        for (Mapping mapping : mappingsToLoad) {
            Property property = descriptor.getProperty(mapping);
            property.setValue(persistentEntity, property.getValue(entityWithUpdates));
        }
    }

    /**
     * Updates the given entity in the database if there are any changes.
     *
     * @param entity the entity to update in the database
     * @param batch  <tt>true</tt> if batch updates should be used, <tt>false</tt> otherwise
     * @return the updated entity or, if batch updates are active, the given entity
     */
    protected E updateIfChanged(E entity, boolean batch) {
        if (entity.isChanged(mappingsToLoad)) {
            getUpdateQuery().update(entity, true, batch);
        }

        return entity;
    }

    /**
     * Creates the prepared statement as {@link FindQuery} used to lookup entities.
     *
     * @return the find query to use
     */
    @SuppressWarnings("unchecked")
    protected FindQuery<E> getFindQuery() {
        if (findQuery == null) {
            findQuery = context.getBatchContext().findQuery((Class<E>) descriptor.getType(), mappingsToFind);
        }

        return findQuery;
    }

    /**
     * Creates the prepared statement as {@link UpdateQuery} used to update entities.
     *
     * @return the update query to use
     */
    @SuppressWarnings("unchecked")
    protected UpdateQuery<E> getUpdateQuery() {
        if (updateQuery == null) {
            updateQuery = context.getBatchContext()
                                 .updateQuery((Class<E>) descriptor.getType(), mappingsToCompare)
                                 .withUpdatedMappings(mappingsToLoad);
        }

        return updateQuery;
    }

    /**
     * Creates the prepared statement as {@link UpdateQuery} used to insert entities.
     *
     * @return the insert query to use
     */
    @SuppressWarnings("unchecked")
    protected InsertQuery<E> getInsertQuery() {
        if (insertQuery == null) {
            insertQuery = context.getBatchContext().insertQuery((Class<E>) descriptor.getType(), true, mappingsToLoad);
        }

        return insertQuery;
    }
}
