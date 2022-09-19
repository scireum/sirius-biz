/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.commons.Callback;

import javax.annotation.Nullable;

/**
 * Represents a computer which can be added to {@link TimeSeriesChartFactory#computers(boolean, boolean, Callback)}.
 * <p>
 * These are the work-horses of the time-series charts, as they compute the actual values.
 *
 * @param <O> the type of entities expected by this computer
 * @see MetricTimeSeriesComputer
 */
public interface TimeSeriesComputer<O> {

    /**
     * Computes the values for the given object and time series.
     *
     * @param object     the object selected for the chart
     * @param timeSeries the time series which specifies the period to query and also permits to create the resulting
     *                   {@link TimeSeriesData} to output date into the chart
     * @throws Exception in case of any error during the computation
     */
    void compute(@Nullable O object, TimeSeries timeSeries) throws Exception;
}
