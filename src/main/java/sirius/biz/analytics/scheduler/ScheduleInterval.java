/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import sirius.kernel.nls.NLS;

/**
 * Specifies the desired execution interval of an {@link AnalyticsScheduler}.
 */
public enum ScheduleInterval {

    DAILY, MONTHLY;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
