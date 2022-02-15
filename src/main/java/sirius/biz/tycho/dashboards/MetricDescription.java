/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.kernel.di.std.Priorized;

public class MetricDescription {

    protected String providerName;
    protected String targetName;
    private String metricName = "-";
    private int priority = Priorized.DEFAULT_PRIORITY;
    private boolean important;
    private String label;
    private String description;

    public MetricDescription(String label) {
        this.targetName = "-";
        this.label = label;
    }

    public MetricDescription withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public MetricDescription markImportant() {
        this.important = true;
        return this;
    }

    public MetricDescription withMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

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
