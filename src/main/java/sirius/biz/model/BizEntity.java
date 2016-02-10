/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.mixing.Column;
import sirius.mixing.Entity;

/**
 * Created by aha on 08.05.15.
 */
public abstract class BizEntity extends Entity {

    public static final String NEW = "new";

    private final TraceData trace = new TraceData();
    public static final Column TRACE = Column.named("trace");

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
