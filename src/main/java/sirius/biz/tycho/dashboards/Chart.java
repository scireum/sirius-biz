/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.web.services.JSONStructuredOutput;

/**
 * Represents a chart which is generated by {@link ChartProvider#resolveChart(String, String)}.
 * <p>
 * A chart is rendered on the metrics dashboard of its target. For a user, a line chart might be "number of logins
 * per month" as line chart.
 */
public interface Chart {

    /**
     * Generates the JSON representation required by the UI to render the chart.
     *
     * @param output the target to write the JSON to
     */
    void writeJson(JSONStructuredOutput output);
}