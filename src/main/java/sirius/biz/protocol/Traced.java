/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Marks an entity as traced.
 * <p>
 * A traced entity provides a {@link TraceData}.
 */
@SuppressWarnings("squid:S1214")
@Explain("The constant is best kept here for consistency reasons.")
public interface Traced {

    /**
     * Provides the default mapping for accessing the trace data.
     */
    Mapping TRACE = Mapping.named("trace");

    /**
     * Returns tracing data which records which user created and last edited the entity
     */
    TraceData getTrace();
}
