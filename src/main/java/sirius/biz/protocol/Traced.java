/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

/**
 * Marks an entity as traced.
 * <p>
 * A traced entity provides a {@link TraceData}.
 */
public interface Traced {

    /**
     * Returns tracing data which records which user created and last edited the entity
     */
    TraceData getTrace();
}
