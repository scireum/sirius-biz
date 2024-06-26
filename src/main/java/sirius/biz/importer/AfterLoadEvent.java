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
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Triggered within {@link ImportHandler#load(Context, BaseEntity)} in order to update an entity
 * using the given context.
 * <p>
 * Note that this is triggered after loading the data into the entity. Therefore the entity should
 * be modified by the context shouldn't.
 *
 * @param <E> the type of entity being updated
 */
public class AfterLoadEvent<E extends Entity> extends ContextScriptableEvent<E> {
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
    public AfterLoadEvent(E entity, Context context, ImporterContext importerContext) {
        super((Class<E>) entity.getClass(), context, importerContext);
        this.entity = entity;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public E getEntity() {
        return entity;
    }

    @Override
    public String toString() {
        return "AfterLoadEvent: "
               + getType().getName()
               + " into "
               + entity
               + "(ID: "
               + entity.getIdAsString()
               + ") with context: "
               + getContext();
    }
}
