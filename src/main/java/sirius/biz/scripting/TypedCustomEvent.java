/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

/**
 * Provides a base class for all custom events which can be internally typed to a specified type.
 * <p>
 * This might be used to events which are the same for many classed (e.g. update events for entities).
 *
 * @param <T> the type of object for which this event occurred.
 */
public abstract class TypedCustomEvent<T> extends CustomEvent {

    /**
     * The of objects for which this event occurred.
     *
     * @return the type of object for which this event occurred
     */
    public abstract Class<T> getType();
}
