/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.importer.format.ImportDictionary;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
     * Returns the dictionary which can be used to map aliases when importing fields.
     *
     * @return the dictionary to be used when mapping incoming data
     */
    ImportDictionary getImportDictionary();

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
     * @throws sirius.kernel.health.HandledException if no matching entity was found
     */
    E findOrFail(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> - creates new entity if no match was found.
     * <p>
     * Loads the given data into the found or created entity.
     *
     * @param data the data used to describe the entity to find
     * @return the matching entity or a newly created and not yet persisted one if no match was found
     */
    E findAndLoad(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> - creates, loads and persists a new entity if no match
     * was found.
     *
     * @param data the data used to describe the entity to find
     * @return the matching entity or a newly created and persisted one if no match was found
     */
    E findOrLoadAndCreate(Context data);

    /**
     * Tries to find an entity using the supplied <tt>data</tt> and the underlying cache - creates, loads and
     * persists a new entity if no match was found.
     *
     * @param data the data used to describe the entity to find
     * @return the matching cached entity or a newly created and persisted one if no match was found
     */
    E findInCacheOrLoadAndCreate(Context data);

    /**
     * Permits to enforce some constraints before persisting an entity.
     * <p>
     * This is autoamtically invoked by {@link #createOrUpdateNow(BaseEntity)} and all other save methods. It is however
     * made public as some jobs only verify the consistency of data without persisting anything.
     * <p>
     * This method should only rarely be overwritten as most checks should be either be performed during a load
     * or within the <tt>beforeSave</tt> checks of the entity. The main point of this method is enforcing the
     * correct tenant before persisting data, as some import handlers might yield (readonly) entities from parent
     * tenants.
     *
     * @param entity the entity to verify
     */
    void enforcePreSaveConstraints(E entity);

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

    /**
     * Either persists or updates the given entity - using a batch update if possible.
     * <p>
     * If the entity doesn't exist yet, it will be immediatelly created. If it doesn exist,
     * it will be updated in the next batch execution. This can be used to ensure that entities
     * referenced by others are available but also that updates of non-critical fields are efficiently
     * executed as a JDBC batch.
     *
     * @param entity the entity to update or persist.
     */
    void createNowOrUpdateInBatch(E entity);

    /**
     * Deletes the given entity.
     *
     * @param entity the entity to delete
     */
    void deleteNow(E entity);

    /**
     * Deletes the given entity - using a batch mode if possible.
     *
     * @param entity the entity to delete
     */
    void deleteInBatch(E entity);

    /**
     * Forces a batch to be processed (independent of it size, as long as it isn't empty).
     */
    void commit();

    /**
     * Returns the dictionary which can be used to map aliases when exporting fields.
     *
     * @return the dictionary to be used when mapping outgoing data
     */
    ImportDictionary getExportDictionary();

    /**
     * Returns the default export mapping (list of columns) to use.
     *
     * @return the list of columns to be exported unless to user as provided a custom column order.
     */
    List<String> getDefaultExportMapping();

    /**
     * Creates an extractor which determines the exportable value for a given field / column.
     *
     * @param fieldToExport the field or column to export
     * @return a function which extracts the field to be exported from a given entity
     */
    Function<? super E, ?> createExtractor(String fieldToExport);

    /**
     * Returns the default representation of an entity of this type with the given id.
     * <p>
     * This is used to fill in a column value for a {@link sirius.db.mixing.types.BaseEntityRef}.
     *
     * @param entityId the id to lookup
     * @return the column value to return for this id.
     */
    Object renderExportRepresentation(Object entityId);
}
