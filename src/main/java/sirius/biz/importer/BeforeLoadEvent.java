/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Entity;
import sirius.kernel.commons.Context;

/**
 * Triggered within {@link ImportHandler#load(Context, BaseEntity)} in order to update an entity
 * using the given context.
 * <p>
 * Note that this is triggered before actually loading the data into the entity. Therefore the context should
 * be modified by the entity shouldn't.
 *
 * @param <E> the type of entity being updated
 */
public class BeforeLoadEvent<E extends Entity> extends ContextScriptableEvent<E> {
    private final E entity;

    /**
     * Creates a new event for the given entity
     *
     * @param entity          the entity to update
     * @param context         the context to read data from. Note that this can and should be modified by the handler,
     *                        as this is the whole purpose of this event anyway.
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     */
    @SuppressWarnings("unchecked")
    public BeforeLoadEvent(E entity, Context context, ImporterContext importerContext) {
        super((Class<E>) entity.getClass(), context, importerContext);
        this.entity = entity;
    }

    public E getEntity() {
        return entity;
    }

    @Override
    public String toString() {
        return "BeforeLoadEvent: "
               + getType().getName()
               + " into "
               + entity
               + "(ID: "
               + entity.getIdAsString()
               + ") with context: "
               + getContext();
    }
}
