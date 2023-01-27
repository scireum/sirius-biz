/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.biz.analytics.explorer.DataExplorerController;
import sirius.kernel.nls.NLS;

/**
 * Defines the units or resolutions understood by {@link Timeseries}.
 *
 * @deprecated Use the {@link DataExplorerController Data-Explorer} for advanced
 * charts and statistics.
 */
@Deprecated
public enum Unit {
    HOUR(60 * 60), DAY(HOUR.getEstimatedSeconds() * 24), WEEK(DAY.getEstimatedSeconds() * 7),
    MONTH(DAY.getEstimatedSeconds() * 30), YEAR(DAY.getEstimatedSeconds() * 365);

    private final int seconds;

    Unit(int seconds) {
        this.seconds = seconds;
    }

    /**
     * Returns the estimated length of the unit in seconds.
     * <p>
     * Can be used to estimate the number of intervals generated when dividing a duration of seconds by this value.
     *
     * @return the estimated length of this unit
     */
    public int getEstimatedSeconds() {
        return seconds;
    }

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
