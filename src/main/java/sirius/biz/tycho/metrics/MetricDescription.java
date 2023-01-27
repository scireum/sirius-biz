/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.metrics;

import sirius.biz.analytics.metrics.Metrics;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;

/**
 * Provides a description of a {@link KeyMetric} to load.
 * <p>
 * The {@link KeyMetrics} framework only determines the metadata of the key metrics to be shown. This data is
 * represented by a <tt>MetricDescription</tt>. The description contains all information required to render the outline
 * of the chart or key metric and also all parameters to call the {@link MetricsApiController}. This controller will
 * then invoke the {@link KeyMetricProvider} to load the actual data. This way, even if loading the metrics data takes a
 * short amount of time, the rendered page is never slowed down.
 */
public class MetricDescription {

    @Part
    private static Metrics metrics;

    /**
     * Contains the {@link KeyMetricProvider#getName()}. Note that this field
     * if filled internally by {@link KeyMetrics}.
     */
    protected String providerName;

    /**
     * Contains the name of the target element for which this description was created. Note that this field
     * if filled internally by {@link KeyMetrics}.
     */
    protected String targetName;

    private String label;

    private String metricName = "-";
    private int priority = Priorized.DEFAULT_PRIORITY;
    private boolean important;
    private String description;

    protected MetricDescription(String providerName, String targetName) {
        this.providerName = providerName;
        this.targetName = targetName;
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
     * Important metrics (esp. key metrics) are shown in additional positions, like the sidebar of an entity editor.
     * All other metrics (including the significant ones) are shown in the statistics/metrics area for an entity. These
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
     * In case a provider supplies more than one key metric, this name is passed into the <tt>resolve</tt>
     * method to decide which metric is requested. Note that this will also pre-fill a label and description
     * using {@link Metrics#fetchLabel(String)} and {@link Metrics#fetchDescription(String)} is no label is currently
     * present.
     *
     * @param metricName the name of the metric to pass into the provider when fetching the data
     * @return the description itself for fluent method calls
     */
    public MetricDescription withMetricName(String metricName) {
        this.metricName = metricName;
        if (Strings.isEmpty(label)) {
            this.label = metrics.fetchLabel(metricName);
            this.description = metrics.fetchDescription(metricName);
        }

        return this;
    }

    /**
     * Specifies a translated label to be shown for the metric.
     * <p>
     * Note that if a {@link #withMetricName(String) metric name} is used and the label is left empty,
     * {@link Metrics#fetchLabel(String)} will be used to derive a label.
     *
     * @param label the label to show
     * @return the description itself for fluent method calls
     */
    public MetricDescription withLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Specifies a translated short description to be shown for the metric.
     * <p>
     * Note that if a {@link #withMetricName(String) metric name} is used and the label is left empty,
     * {@link Metrics#fetchDescription(String)} will be used to derive a description.
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
