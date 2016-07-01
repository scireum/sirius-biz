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
 * Next to having {@link TraceData} built in, it provides a {@link #getIdAsString()} method which returns
 * the database ID as string. However, for new entities (not yet persisted), it returns "new" which is
 * recognized by the {@link sirius.biz.web.BizController}.
 * <p>
 * Therefore a simple editor which responds to an URL like "/entity/ID" can be called via "/entity/new" to create a new
 * entity.
 */
public abstract class BizEntity extends Entity {

    /**
     * Contains the constant used to mark a new (unsaved) entity.
     */
    public static final String NEW = "new";

    /**
     * Contains tracing data which records which user created and last edited the entity
     */
    public static final Column TRACE = Column.named("trace");
    private final TraceData trace = new TraceData();

    /**
     * Returns a string representation of the entity ID.
     * <p>
     * If the entity is new, "new" will be returned.
     *
     * @return the entity ID as string or "new" if the entity {@link #isNew()}.
     */
    public String getIdAsString() {
        if (isNew()) {
            return NEW;
        }
        return String.valueOf(getId());
    }

    public TraceData getTrace() {
        return trace;
    }
}
