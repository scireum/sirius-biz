/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.kernel.di.std.Priorized;

/**
 * Provides a description of a {@link Chart} or {@link KeyMetric} to load.
 * <p>
 * The {@link MetricsDashboard} framework only determines the metadata of the charts and key metrics to be shown. This
 * data is represents by a <tt>MetricDescription</tt>. The description contains all information required to render the
 * outline of the chart or key metric and also all parameters to call the {@link MetricsApiController}. This controller
 * will then invoke the {@link KeyMetricProvider} or {@link ChartProvider} to load the actual data. This way, even
 * if loading the metrics data takes a short amount of time, the rendered page is never slowed down.
 */
public class MetricDescription {

    /**
     * Contains the {@link KeyMetricProvider#getName()} or {@link ChartProvider#getName()}. Note that this field
     * if filled internally.
     */
    protected String providerName;

    /**
     * Contains the name of the target element for which this description was created.  Note that this field
     * if filled internally.
     */
    protected String targetName;

    private final String label;

    private String metricName = "-";
    private int priority = Priorized.DEFAULT_PRIORITY;
    private boolean important;
    private String description;

    /**
     * Creates a metric description with the given label to show.
     *
     * @param label the label to show to the user
     */
    public MetricDescription(String label) {
        this.targetName = "-";
        this.label = label;
    }

    /**
     * Specifies the sort priority for this metric.
     *
     * @param priority the priority to apply. Note that the default is {@link Priorized#DEFAULT_PRIORITY}.
     * @return the description itself for fluent method calls
     */
    public MetricDescription withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Marks this metric as important.
     * <p>
     * Important metric (esp. key metrics) are shown in additional positions, like the sidebar of an entity editor.
     * All other metrics (including the important ones) are shown in the statistics/metrics area for an entity. These
     * UIs are built using the appropriate tags.
     *
     * @return the description itself for fluent method calls
     */
    public MetricDescription markImportant() {
        this.important = true;
        return this;
    }

    /**
     * Specifies the metric name to use.
     * <p>
     * In case a provider supplies more than one chart or key metric, this name is passed into the <tt>resolve</tt>
     * method to decide which metric is requested.
     *
     * @param metricName the name of the metric to pass into the provider when fetching the data
     * @return the description itself for fluent method calls
     */
    public MetricDescription withMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    /**
     * Specifies a translated short description to be shown for the chart or metric.
     *
     * @param description the description to show
     * @return the description itself for fluent method calls
     */
    public MetricDescription withDescription(String description) {
        this.description = description;
        return this;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getMetricName() {
        return metricName;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isImportant() {
        return important;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
