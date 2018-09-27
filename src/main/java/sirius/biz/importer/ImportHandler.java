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

import java.util.Optional;

/**
 * Executes the operations provided by {@link Importer} for a specific type of entities.
 * <p>
 * A new handler can be provided by implementing an appropriate {@link ImportHandlerFactory} which must be made
 * available to the dependency injection framework using {@link sirius.kernel.di.std.Register}.
 * <p>
 * Which import handle is actually used for a type is determined by {@link ImporterContext#findHandler(Class)}. As the
 * factories are priorized, an existing handler can be overwritten by providing a customization which supplies a
 * factory with a lower {@link ImportHandlerFactory#getPriority() priority} for the same entity type.
 * <p>
 * An example which provides basic support for all sql based entities can be found in {@link SQLEntityImportHandler}.
 *
 * @param <E> the generic type of entities being handled by this import handler
 */
public interface ImportHandler<E extends BaseEntity<?>> {

    /**
     * Fills the given entity it using the supplied <tt>data</tt>.
     *
     * @param data   used to fill the newly created entity
     * @param entity the entity to be filled
     * @return a new and not yet persisted entity filled with value from <tt>data</tt>
     */
    E load(Context data, E entity);

    /**
     * Tries to find an entity using the supplied <tt>data</tt>.
     *
     * @param data the data used to describe the entity to find
     * @return a matching entity wrapped as optional or an empty optional if there is no matching entity
     */
    Optional<E> tryFind(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> while utilizing the {@link ImporterContext#getLocalCache()
     * local cache of the import}.
     *
     * @param data the data used to describe the entity to find
     * @return a matching entity wrapped as optional or an empty optional if there is no matching entity
     */
    Optional<E> tryFindInCache(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> - throws an exception if no matching entity was found.
     *
     * @param data the data used to describe the entity to find
     * @return the matching entity
     * @throws sirius.kernel.health.HandledException if no matching enttiy was found
     */
    E findOrFail(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> - creates and loads a new entity if no match was found.
     *
     * @param data the data used to describe the entity to find
     * @return the matching entity or a newly created and not yet persisted one if no match was found
     */
    E findOrLoad(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> - creates, loads and persists a new entity if no match
     * was found.
     *
     * @param data the data used to describe the entity to find
     * @return the matching entity or a newly created and persisted one if no match was found
     */
    E findOrLoadAndCreate(Context data);

    /**
     * Either persists or updates the given entity.
     *
     * @param entity the entity to update or persist.
     * @return the updated or persisted entity
     */
    E createOrUpdateNow(E entity);

    /**
     * Either persists or updates the given entity - using a batch update if possible.
     *
     * @param entity the entity to update or persist.
     */
    void createOrUpdateInBatch(E entity);
}
