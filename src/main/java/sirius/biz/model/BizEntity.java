/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.protocol.NoJournal;
import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.kernel.commons.Strings;

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

    /**
     * Checks whether any {@link Column} (except {@link NoJournal no journal data}) of the current {@link BizEntity} changed.
     *
     * @return <tt>true</tt> if at least one column was changed, <tt>false</tt> otherwise.
     */
    public boolean isAnyColumnChangedExceptNoJournal() {
        return getDescriptor().getProperties()
                              .stream()
                              .anyMatch(property -> getDescriptor().isChanged(this, property)
                                                    && property.getAnnotation(NoJournal.class) == null);
    }
}
