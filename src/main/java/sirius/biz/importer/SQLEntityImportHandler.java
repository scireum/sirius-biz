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
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SQLEntityImportHandler<E extends SQLEntity> extends BaseImportHandler<E> {

    private static final Mapping[] MAPPING_ARRAY = new Mapping[0];
    private FindQuery<E> findQuery;
    private UpdateQuery<E> updateQuery;
    private InsertQuery<E> insertQuery;

    private Mapping[] mappingsToLoad;
    private Mapping[] mappingsToFind;
    private Mapping[] mappingsToCompare;

    public SQLEntityImportHandler(ImportContext context) {
        super(context);
        this.mappingsToLoad = getMappingsToLoad().toArray(MAPPING_ARRAY);
        this.mappingsToFind = getMappingsToFind().toArray(MAPPING_ARRAY);
        this.mappingsToCompare = getMappingsToCompare().toArray(MAPPING_ARRAY);
    }

    protected List<Mapping> getMappingsToFind() {
        return Collections.singletonList(SQLEntity.ID);
    }

    protected List<Mapping> getMappingsToCompare() {
        return Collections.singletonList(SQLEntity.ID);
    }

    protected List<Mapping> getMappingsToLoad() {
        return descriptor.getProperties()
                         .stream()
                         .map(Property::getName)
                         .map(Mapping::named)
                         .filter(Predicate.isEqual(SQLEntity.ID).negate())
                         .collect(Collectors.toList());
    }

    @Override
    public E load(Context data) {
        return load(data, mappingsToLoad);
    }

    @Override
    public Optional<E> tryFind(Context data) {
        return getFindQuery().find(load(data, mappingsToFind));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<E> tryFindInCache(Context data) {
        E example = load(data, mappingsToFind);
        String cacheKey = determineCacheKey(example);
        if (Strings.isFilled(cacheKey)) {
            Object result = context.getLocalCache().getIfPresent(cacheKey);
            if (result != null) {
                return Optional.of((E) result);
            }
        }

        Optional<E> result = getFindQuery().find(load(data, mappingsToFind));
        if (result.isPresent() && Strings.isFilled(cacheKey)) {
            context.getLocalCache().put(cacheKey, result.get());
        }

        return result;
    }

    protected String determineCacheKey(E example) {
        return null;
    }

    @SuppressWarnings({"unchecked"})
    private FindQuery<E> getFindQuery() {
        if (findQuery == null) {
            findQuery = context.getBatchContext().findQuery((Class<E>) descriptor.getType(), mappingsToFind);
        }

        return findQuery;
    }

    @Override
    public E createOrUpdateNow(E entity) {
        return createOrUpdate(entity, false);
    }

    protected E createOrUpdate(E entity, boolean batch) {
        E entityToUpdate = getFindQuery().find(entity).orElse(null);
        if (entityToUpdate == null) {
            if (entity.isChanged(mappingsToLoad)) {
                getInsertQuery().insert(entity, true, batch);
            }
        } else {
            Arrays.stream(mappingsToLoad)
                  .map(descriptor::getProperty)
                  .forEach(property -> property.setValue(entityToUpdate, property.getValue(entity)));
            if (entityToUpdate.isChanged(mappingsToLoad)) {
                getUpdateQuery().update(entityToUpdate, true, batch);
            }
        }

        return entity;
    }

    @SuppressWarnings("unchecked")
    private UpdateQuery<E> getUpdateQuery() {
        if (updateQuery == null) {
            updateQuery = context.getBatchContext()
                                 .updateQuery((Class<E>) descriptor.getType(), mappingsToCompare)
                                 .withUpdatedMappings(mappingsToLoad);
        }

        return updateQuery;
    }

    @SuppressWarnings("unchecked")
    private InsertQuery<E> getInsertQuery() {
        if (insertQuery == null) {
            insertQuery = context.getBatchContext().insertQuery((Class<E>) descriptor.getType(), true, mappingsToLoad);
        }

        return insertQuery;
    }

    @Override
    public void createOrUpdateInBatch(E entity) {
        createOrUpdate(entity, true);
    }
}
