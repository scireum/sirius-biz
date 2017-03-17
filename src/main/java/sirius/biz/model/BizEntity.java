/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;

/**
 * Provides a base class for entities managed by a {@link sirius.biz.web.BizController}.
 * <p>
 * Provides built in {@link TraceData}
 */
public abstract class BizEntity extends Entity {

    /**
     * Contains tracing data which records which user created and last edited the entity
     */
    public static final Column TRACE = Column.named("trace");
    private final TraceData trace = new TraceData();

    public TraceData getTrace() {
        return trace;
    }
}
