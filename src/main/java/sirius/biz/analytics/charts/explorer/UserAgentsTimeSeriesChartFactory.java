/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.Event;
import sirius.biz.jobs.StandardCategories;
import sirius.db.jdbc.SmartQuery;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a base class for user agent based time series charts.
 */
public abstract class UserAgentsTimeSeriesChartFactory<E extends Event> extends TimeSeriesChartFactory<Object> {
    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Object>> getResolver() {
        return null;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MISC;
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        // intentionally empty -> there are no referenced charts
    }

    @Override
    protected boolean stackValues(boolean hasComparisonPeriod) {
        return true;
    }

    protected abstract Class<E> getEvent();

    protected abstract void modifyQuery(SmartQuery<E> query);
}
