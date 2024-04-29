/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.scripting.TypedScriptableEvent;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Entity;

/**
 * Triggered within {@link ImportHandler#createOrUpdateNow(BaseEntity)} (or the batch equivalent) in order to update an
 * entity using the given context.
 *
 * @param <E> the type of entity being updated
 */
public class BeforeCreateOrUpdateEvent<E extends Entity> extends TypedScriptableEvent<E> {
    private final E entity;
    private final ImporterContext importerContext;

    /**
     * Creates a new event for the given entity
     *
     * @param entity          the entity to update
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     */
    public BeforeCreateOrUpdateEvent(E entity, ImporterContext importerContext) {
        this.importerContext = importerContext;
        this.entity = entity;
    }

    public E getEntity() {
        return entity;
    }

    @Override
    public String toString() {
        return "BeforeCreateOrUpdateEvent: "
               + getType().getName()
               + " with entity "
               + entity
               + "(ID: "
               + entity.getIdAsString()
               + ")";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<E> getType() {
        return (Class<E>) entity.getClass();
    }

    public ImporterContext getImporterContext() {
        return importerContext;
    }
}
