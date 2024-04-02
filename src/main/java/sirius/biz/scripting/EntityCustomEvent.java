/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.db.mixing.Entity;

/**
 * Provides a base class for all custom events which are associated with an entity.
 *
 * @param <E> the generic type of the entity
 */
public abstract class EntityCustomEvent<E extends Entity> extends TypedCustomEvent<E> {

    private final E entity;

    protected EntityCustomEvent(E entity) {
        this.entity = entity;
    }

    /**
     * Returns the entity associated with this event.
     *
     * @return the entity associated with this event
     */
    public E getEntity() {
        return entity;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<E> getType() {
        return (Class<E>) entity.getClass();
    }
}
