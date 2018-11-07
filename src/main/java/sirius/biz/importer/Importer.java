/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * Helps to import semi-structured data into {@link BaseEntity entities} while providing some convenience methods
 * which are implemented in {@link ImportHandler import handlers}.
 * <p>
 * As the importer tries to optimize the process as much as possible, {@link #close()} has to be called at the
 * end to ensure that batches and prepared statements are properly executed and closed.
 */
public class Importer implements Closeable {

    protected ImporterContext context;
    protected String name;

    /**
     * Import log which is accessible to all import jobs.
     */
    public static final Log LOG = Log.get("importer");

    /**
     * Creates a new importer.
     * <p>
     * The given name should be a short description of what it is used for, as it might show up in system diagnosis
     * utilities.
     *
     * @param name a short description of what the importer is used for.
     */
    public Importer(String name) {
        this.name = name;
        this.context = new ImporterContext(this);
    }

    /**
     * Loads all relevant fields of the given data into a new entity of the given type.
     * <p>
     * Note that this entity is not persisted yet. Also note, that the properties which are actually filled
     * are determined by the {@link ImportHandler} being used.
     *
     * @param type the type of entity to create.
     * @param data the data to read the values from
     * @param <E>  the generic type of the entity to create
     * @return a new and non persisted entity of the given type, pre-filled with the given data
     */
    public <E extends BaseEntity<?>> E load(Class<E> type, Context data) {
        try {
            E entity = type.getConstructor().newInstance();
            return load(type, data, entity);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw Exceptions.handle()
                            .error(e)
                            .withSystemErrorMessage("Cannot create an instance of: %s", type.getName())
                            .handle();
        }
    }

    /**
     * Loads all relevant fields of the given data into a given entity of the given type.
     * <p>
     * Note that the properties which are actually filled are determined by the {@link ImportHandler} being used.
     * Also note, that if the given entity is null a new entity is created.
     * <p>
     * The changes to the entity are not persisted. This must be done by an extra create or update call.
     *
     * @param type   the type of entity to create.
     * @param data   the data to read the values from
     * @param entity the entity to load the relevant fields into
     * @param <E>    the generic type of the entity to create
     * @return a new and non persisted entity of the given type, pre-filled with the given data
     */
    public <E extends BaseEntity<?>> E load(Class<E> type, Context data, E entity) {
        if (entity == null) {
            return load(type, data);
        }

        return context.findHandler(type).load(data, entity);
    }

    /**
     * Tries to find an instance of the given type based on the given data.
     * <p>
     * Note that the {@link ImportHandler} being used controls which values of <tt>data</tt> are used to resolveFromString the
     * entity.
     *
     * @param type the type of entity to find
     * @param data the data used to describe the entity to search
     * @param <E>  the generic type of the entity to find
     * @return the matching entity wrapped as optional or an emty optional if none was found
     */
    public <E extends BaseEntity<?>> Optional<E> tryFind(Class<E> type, Context data) {
        return context.findHandler(type).tryFind(data);
    }

    /**
     * Tries to find an instance of the given type just like {@link #tryFind(Class, Context)} while using a cache.
     * <p>
     * If a lookup is successful and the utilized {@link ImportHandler} permits its usage, the result is placed in a
     * cache and used for the next lookup.
     * <p>
     * Note that the cache is never flushed and is maintained along with the {@link Importer}. Therefore this should
     * only be used, if the entities resolved by this method are not changed using this importer.
     *
     * @param type the type of entity to find
     * @param data the data used to describe the entity to search
     * @param <E>  the generic type of the entity to find
     * @return the matching entity wrapped as optional or an emty optional if none was found
     */
    public <E extends BaseEntity<?>> Optional<E> tryFindInCache(Class<E> type, Context data) {
        return context.findHandler(type).tryFindInCache(data);
    }

    /**
     * Tries to find an instace of the given type based on the given data. Fails if no entity matches.
     * <p>
     * This is a convenience method which utilizes {@link #tryFind(Class, Context)} but throws an exception instead of
     * returning an empty optional when no matching entity was found.
     *
     * @param type the type of entity to find
     * @param data the data used to describe the entity to search
     * @param <E>  the generic type of the entity to find
     * @return the matching entity
     * @throws sirius.kernel.health.HandledException if no matching entity was found
     */
    public <E extends BaseEntity<?>> E findOrFail(Class<E> type, Context data) {
        return context.findHandler(type).findOrFail(data);
    }

    /**
     * Tries to find an instance of the given type based on the given data. Uses {@link #load(Class, Context)} if no
     * matching entity was found.
     * <p>
     * This is a convenience method which utilizes {@link #tryFind(Class, Context)} but uses
     * {@link #load(Class, Context)} to create a new entity if the lookup isn't successful.
     *
     * @param type the type of entity to find
     * @param data the data used to describe the entity to search
     * @param <E>  the generic type of the entity to find
     * @return either a matching entity or a new and not yet peristed entity loaded from the given data
     */
    public <E extends BaseEntity<?>> E findAndLoad(Class<E> type, Context data) {
        return context.findHandler(type).findAndLoad(data);
    }

    /**
     * Tries to find an instance of the given type based on the given data. Loads and creates a new entity if no
     * matching entity was found.
     * <p>
     * This is a convenience method which utilizes {@link #tryFind(Class, Context)} but uses
     * {@link #load(Class, Context)} and {@link #createOrUpdateNow(BaseEntity)} to create and persist a new entity if
     * the lookup isn't successful.
     *
     * @param type the type of entity to find
     * @param data the data used to describe the entity to search
     * @param <E>  the generic type of the entity to find
     * @return either a matching entity or a newly created and persisted entity loaded from the given data
     */
    public <E extends BaseEntity<?>> E findOrLoadAndCreate(Class<E> type, Context data) {
        return context.findHandler(type).findOrLoadAndCreate(data);
    }

    /**
     * Creates or updates the given entity in the underlying database.
     * <p>
     * Uses the appropriate {@link ImportHandler} to determine if the entity already exists and then either updates
     * the entity or creates a new one.
     *
     * @param entity the entity to update or create
     * @param <E>    the generic type of the entity
     * @return the newly created or updated entity
     */
    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> E createOrUpdateNow(E entity) {
        return context.findHandler((Class<E>) entity.getClass()).createOrUpdateNow(entity);
    }

    /**
     * Creates or updates the given entity in the underlying database using a batch update
     * <p>
     * Uses the appropriate {@link ImportHandler} to determine if the entity already exists and then either updates
     * the entity or creates a new one.
     * <p>
     * Using a batch update will most probably yield substantial performance benefits with the downside, that the updated
     * entity cannot be returned by this method.
     *
     * @param entity the entity to update or create
     * @param <E>    the generic type of the entity
     */
    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> void createOrUpdateInBatch(E entity) {
        context.findHandler((Class<E>) entity.getClass()).createOrUpdateInBatch(entity);
    }

    @Override
    public void close() throws IOException {
        context.close();
    }

    /**
     * Deletes the given entity.
     *
     * @param entity the entity to delete
     * @param <E>    the generic type of the entity
     */
    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> void deleteNow(E entity) {
        context.findHandler((Class<E>) entity.getClass()).deleteNow(entity);
    }

    /**
     * Deletes the given entity - using a batch mode if possible.
     *
     * @param entity the entity to delete
     * @param <E>    the generic type of the entity
     */
    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> void deleteInBatch(E entity) {
        context.findHandler((Class<E>) entity.getClass()).deleteInBatch(entity);
    }

    /**
     * Resolves which {@link ImportHandler} to use for a given type.
     *
     * @param type the entity type to find the appropriate handler for.
     * @param <E>  the generic type of the entity class
     * @return the appropriate handler for this type
     * @throws sirius.kernel.health.HandledException if no appropriate handler is available
     */
    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> ImportHandler<E> findHandler(Class<E> type) {
        return context.findHandler(type);
    }
}
