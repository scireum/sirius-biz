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
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

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
    protected static Mango mango;

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
        E result = load(data, entity, mappingsToLoad);

        if (context.getEventDispatcher().isActive()) {
            context.getEventDispatcher()
                   .handleEvent(new OnLoadEvent<E>(result, data, context));
        }

        return result;
    }

    @Override
    protected E load(Context data, E entity, Mapping... mappings) {
        E loadedEntity = super.load(data, entity, mappings);
        if (loadedEntity instanceof MongoTenantAware mongoTenantAware) {
            mongoTenantAware.fillWithCurrentTenant();
        }

        return loadedEntity;
    }

    @Override
    protected void collectExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(10, MongoEntity.ID);
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        // Provides an empty base implementation as for most entities this can be controlled
        // via @Exportable
    }

    @SuppressWarnings("unchecked")
    @Override
    public final Optional<E> tryFind(Context data) {
        if (context.getEventDispatcher().isActive()) {
            context.getEventDispatcher()
                   .handleEvent(new BeforeFindEvent<E>((Class<E>) descriptor.getType(), data, context));
        }

        return findByExample(data);
    }

    /**
     * Provides the default implementation for {@link #tryFind(Context)}.
     * <p>
     * Most probably, {@link #loadForFind(Context)} and {@link #tryFindByExample(MongoEntity)} should be overwritten
     * instead of this method. However, for a completely custom approach, this method can be overwritten, as tryFind
     * itself is final.
     *
     * @param data the data used to find the entity
     * @return the entity for the given data or an empty optional if no matching entity can be found
     */
    protected Optional<E> findByExample(Context data) {
        E example = loadForFind(data);
        return tryFindByExample(example);
    }

    /**
     * Loads all {@link #mappingsToLoad} and may perform some cleanups if necessary.
     * <p>
     * Some fields are normalized within {@link sirius.db.mixing.annotations.BeforeSave} handlers. This method
     * can be overwritten to perform the same operations so that the values properly match within the
     * find queries.
     *
     * @param data the data used to describe the entity to find
     * @return the example entity which has been populated from the given <tt>data</tt>
     */
    protected E loadForFind(Context data) {
        return load(data, mappingsToLoad);
    }

    /**
     * Tries to find a persisted entity using the given example.
     *
     * @param example the example instance used to search by
     * @return a matching entity wrapped as optional or an empty optional if no match is available
     */
    protected abstract Optional<E> tryFindByExample(E example);

    @Override
    public E createOrUpdateNow(E entity) {
        try {
            if (context.getEventDispatcher().isActive()) {
                context.getEventDispatcher()
                       .handleEvent(new BeforeCreateOrUpdateEntityEvent<E>(entity, context));
            }

            enforcePreSaveConstraints(entity);

            // Invoke the beforeSave checks so that the change-detection below works for
            // computed properties...
            descriptor.beforeSave(entity);

            if (isChanged(entity)) {
                mango.update(entity);
            }

            return entity;
        } catch (HandledException exception) {
            HandledException handledException = Exceptions.createHandled()
                                                          .withDirectMessage(entity.getDescriptor()
                                                                                   .createCannotSaveMessage(exception.getMessage()))
                                                          .handle();
            throw enhanceExceptionWithHints(handledException);
        }
    }

    /**
     * Checks whether any property of the entity has changed.
     *
     * @param entity the entity to check
     * @return <tt>true</tt> if any property checked is changed, <tt>false</tt> otherwise
     */
    protected boolean isChanged(E entity) {
        return entity.isChanged(mappingsToLoad);
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
        enforcePreDeleteConstraints(entity);

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
