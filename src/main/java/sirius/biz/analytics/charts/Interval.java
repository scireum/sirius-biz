/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.biz.analytics.explorer.DataExplorerController;

import javax.annotation.concurrent.Immutable;
import java.time.LocalDateTime;

/**
 * Represents an interval (a start and end date as {@link LocalDateTime}).
 *
 * @deprecated Use the {@link DataExplorerController Data-Explorer} for advanced
 * charts and statistics.
 */
@Deprecated
@Immutable
public class Interval {

    private final LocalDateTime start;
    private final LocalDateTime end;

    /**
     * Creates an interval with the given start and end.
     *
     * @param start the start date and time
     * @param end   the end date and time
     */
    public Interval(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Returns the start of this interval.
     *
     * @return the start date and time
     */
    public LocalDateTime getStart() {
        return start;
    }

    /**
     * Returns the end of this interval.
     *
     * @return the end date and time
     */
    public LocalDateTime getEnd() {
        return end;
    }
}
