/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

/**
 * Represents a line chart used to represent time-series data.
 */
@Deprecated
public class LineChart extends BaseTimeseriesChart {

    @Override
    protected String getChartType() {
        return "LineChart";
    }
}
