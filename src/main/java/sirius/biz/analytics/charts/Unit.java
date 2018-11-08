/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.kernel.nls.NLS;

public enum Unit {
    HOUR(60 * 60), DAY(HOUR.getEstimatedSeconds() * 24), WEEK(DAY.getEstimatedSeconds() * 7),
    MONTH(DAY.getEstimatedSeconds() * 30), YEAR(DAY.getEstimatedSeconds() * 365);

    private final int seconds;

    Unit(int seconds) {
        this.seconds = seconds;
    }

    public int getEstimatedSeconds() {
        return seconds;
    }

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName()+"."+name());
    }
}
