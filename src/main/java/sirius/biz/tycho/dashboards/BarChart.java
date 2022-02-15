/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

/**
 * Represents a bar chart used to represent time-series data.
 */
public class BarChart extends BaseTimeseriesChart {

    @Override
    protected String getChartType() {
        return "BarChart";
    }
}
