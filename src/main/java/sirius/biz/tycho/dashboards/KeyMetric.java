/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.biz.analytics.metrics.MetricQuery;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.NumberFormat;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Represents a key metric to be shown on a metric dashboard.
 * <p>
 * A key metric is a number shown on the metrics dashboard, which might be accompanied with a history, shown as
 * graph below. A key metric for a user might be "number of logins" or "activity in %".
 */
public class KeyMetric {

    private final String value;
    private List<Integer> history = Collections.emptyList();

    /**
     * Creates a key metric with the given value.
     *
     * @param value the actual value of the metric
     */
    public KeyMetric(String value) {
        this.value = value;
    }

    /**
     * Creates a key metric based on the given query.
     *
     * @param query the metric query used to load the actual value
     * @param unit  the (optional) unit to append
     */
    public KeyMetric(MetricQuery query, @Nullable String unit) {
        this(Amount.of(query.lastValue()).toString(NumberFormat.NO_DECIMAL_PLACES).tryAppend(" ", unit).asString());
        withHistory(query);
    }

    /**
     * Adds a history to be shown for the key metric.
     *
     * @param history a list of historical values of the metric
     * @return the metric itself for fluent method calls
     */
    public KeyMetric withHistory(List<Integer> history) {
        this.history = Collections.unmodifiableList(history);
        return this;
    }

    /**
     * Adds a history to be shown for the key metric, based on the given query.
     *
     * @param query the query used to load the historical values
     * @return the metric itself for fluent method calls
     */
    public KeyMetric withHistory(MetricQuery query) {
        this.history = query.valuesUntil(LocalDate.now(), query.determineDefaultLimit());
        return this;
    }

    /**
     * Outputs the key metric as JSON.
     *
     * @param output the output to write the JSON to
     */
    public void writeJson(JSONStructuredOutput output) {
        output.beginObject("metric");
        output.property("value", value);
        output.array("history", "value", this.history);
        output.endObject();
    }
}
