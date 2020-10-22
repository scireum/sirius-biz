/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Register(classes = Dashboards.class)
public class Dashboards {

    @Parts(KeyMetricProvider.class)
    private PartCollection<KeyMetricProvider<?>> keyMetricProviders;

    @Parts(ChartProvider.class)
    private PartCollection<ChartProvider<?>> chartProviders;

    @Part
    private GlobalContext globalContext;

    @SuppressWarnings("unchecked")
    public List<MetricDescription> fetchImportantKeyMetrics(Object target, int limit) {
        List<MetricDescription> result = new ArrayList<>(limit);
        for (KeyMetricProvider<?> provider : keyMetricProviders) {
            if (result.size() < limit && provider.getTargetType().isAssignableFrom(target.getClass())) {
                ((KeyMetricProvider<Object>) provider).collectKeyMetrics(target, description -> {
                    if (description.isImportant() && result.size() < limit) {
                        description.forMetricProvider(provider);
                        result.add(description);
                    }
                });
            }
        }

        result.sort(Comparator.comparing(MetricDescription::getPriority));

        return result;
    }

    @SuppressWarnings("unchecked")
    public List<MetricDescription> fetchAllKeyMetrics(Object target) {
        List<MetricDescription> result = new ArrayList<>();
        for (KeyMetricProvider<?> provider : keyMetricProviders) {
            if (provider.getTargetType().isAssignableFrom(target.getClass())) {
                ((KeyMetricProvider<Object>) provider).collectKeyMetrics(target, description -> {
                    description.forMetricProvider(provider);
                    result.add(description);
                });
            }
        }

        result.sort(Comparator.comparing(MetricDescription::getPriority));

        return result;
    }

    public KeyMetric resolveKeyMetric(String providerName, String targetName, String metricName) {
        return globalContext.findPart(providerName, KeyMetricProvider.class).resolveKeyMetric(targetName, metricName);
    }

    @SuppressWarnings("unchecked")
    public List<MetricDescription> fetchImportantCharts(Object target, int limit) {
        List<MetricDescription> result = new ArrayList<>(limit);
        for (ChartProvider<?> provider : chartProviders) {
            if (result.size() < limit && provider.getTargetType().isAssignableFrom(target.getClass())) {
                ((ChartProvider<Object>) provider).collectCharts(target, description -> {
                    if (description.isImportant() && result.size() < limit) {
                        description.forMetricProvider(provider);
                        result.add(description);
                    }
                });
            }
        }

        result.sort(Comparator.comparing(MetricDescription::getPriority));

        return result;
    }

    @SuppressWarnings("unchecked")
    public List<MetricDescription> fetchAllCharts(Object target) {
        List<MetricDescription> result = new ArrayList<>();
        for (ChartProvider<?> provider : chartProviders) {
            if (provider.getTargetType().isAssignableFrom(target.getClass())) {
                ((ChartProvider<Object>) provider).collectCharts(target, description -> {
                    description.forMetricProvider(provider);
                    result.add(description);
                });
            }
        }

        result.sort(Comparator.comparing(MetricDescription::getPriority));

        return result;
    }

    public Chart resolveChart(String providerName, String targetName, String metricName) {
        return globalContext.findPart(providerName, ChartProvider.class).resolveChart(targetName, metricName);
    }
}
