/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.kernel.nls.NLS;

public enum ComparisonPeriod {

    PREVIOUS_YEAR, PREVIOUS_MONTH, PREVIOUS_WEEK, PREVIOUS_DAY;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }

}
