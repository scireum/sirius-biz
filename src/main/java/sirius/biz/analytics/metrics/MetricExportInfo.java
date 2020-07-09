/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

/**
 * Wraps all infos required to inject a metric into an entity export via a {@link MetricExporter}.
 */
public class MetricExportInfo {

    private final String name;
    private final String label;
    private int priority;
    private boolean defaultExport;
    private ToIntFunction<Object> extractor;

    @Part
    @Nullable
    private static Metrics metrics;

    /**
     * Creates an info for the given metric name and its label.
     *
     * @param name  the name of the metric to export
     * @param label the label of the metric which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    public MetricExportInfo(String name, String label) {
        this.name = name;
        this.label = label;
    }

    /**
     * Creates an info for the given metric name while using the {@link Metrics#fetchLabel(String) default label}.
     *
     * @param name  the name of the metric to export
     */
    public MetricExportInfo(String name) {
        this.name = name;
        this.label = metrics.fetchLabel(name);
    }

    /**
     * Adds the metric to the default export of the entity.
     *
     * @return the info itself for fluent method calls
     */
    public MetricExportInfo markAsDefaultExport() {
        this.defaultExport = true;
        return this;
    }

    /**
     * Specifies a custom sort order for the metric within the export.
     *
     * @param priority the priority to use
     * @return the info itself for fluent method calls
     */
    public MetricExportInfo withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    @SuppressWarnings("unchecked")
    protected MetricExportInfo withExtractor(ToIntFunction<?> extractor) {
        this.extractor = (ToIntFunction<Object>) extractor;
        return this;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isDefaultExport() {
        return defaultExport;
    }

    protected ToIntFunction<Object> getExtractor() {
        return extractor;
    }

    protected Integer getEffectivePriority(AtomicInteger priorityGenerator) {
        if (priority == 0) {
            return priorityGenerator.incrementAndGet();
        } else {
            return priority;
        }
    }
}
