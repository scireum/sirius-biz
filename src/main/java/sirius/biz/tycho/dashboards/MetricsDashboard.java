/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.db.mixing.Entity;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provides a helper to obtain all available {@link KeyMetric key metrics} or {@link Chart charts} for a target.
 * <p>
 * This is achieved by calling all available {@link KeyMetricProvider key metric providers} and
 * {@link ChartProvider chart providers} to load the actual data.
 */
@Register(classes = MetricsDashboard.class)
public class MetricsDashboard {

    @Parts(KeyMetricProvider.class)
    private PartCollection<KeyMetricProvider> keyMetricProviders;

    @Parts(ChartProvider.class)
    private PartCollection<ChartProvider> chartProviders;

    @Part
    private GlobalContext globalContext;

    /**
     * Fetches all available key metrics for the given target.
     * <p>
     * Note that this only loads the {@link MetricDescription} not the actual data, so this call should be quite fast.
     *
     * @param target the target to fetch key metrics for
     * @return the list of available key metrics as descriptors
     */
    public List<MetricDescription> fetchAllKeyMetrics(Object target) {
        String targetName = determineTargetName(target);

        List<MetricDescription> result = new ArrayList<>();
        keyMetricProviders.getParts().stream().filter(KeyMetricProvider::isAccessible).forEach(provider -> {
            provider.collectKeyMetrics(targetName, description -> {
                description.targetName = targetName;
                description.providerName = provider.getName();
                result.add(description);
            });
        });

        result.sort(Comparator.comparing(MetricDescription::getPriority));

        return result;
    }

    private String determineTargetName(Object target) {
        if (target instanceof Class<?> clazz) {
            if (Entity.class.isAssignableFrom(clazz)) {
                return Mixing.getNameForType(clazz);
            } else {
                return clazz.getName();
            }
        }

        if (target instanceof Entity entity) {
            return entity.getUniqueName();
        }

        return target.toString();
    }

    /**
     * Fetches all important key metrics for the given target.
     * <p>
     * Note that this only loads the {@link MetricDescription} not the actual data, so this call should be quite fast.
     * <p>
     * An important key metric has {@link MetricDescription#isImportant()} toggled and will be shown on preview
     * pages like the dashboard itself or in the sidebar of list views.
     *
     * @param target the target to fetch key metrics for
     * @param limit  the maximal number of metrics to fetch
     * @return the list of available key metrics as descriptors
     */
    public List<MetricDescription> fetchImportantKeyMetrics(Object target, int limit) {
        return fetchAllKeyMetrics(target).stream().filter(MetricDescription::isImportant).limit(limit).toList();
    }

    /**
     * Resolves the key metric based on the given parameters.
     *
     * @param providerName the name of the {@link KeyMetricProvider}
     * @param targetName   the target to fetch the metric for
     * @param metricName   the name of the metric in case the provider has several
     * @return the resolved and computed key metric
     */
    public KeyMetric resolveKeyMetric(String providerName, String targetName, String metricName) {
        KeyMetricProvider keyMetricProvider = globalContext.findPart(providerName, KeyMetricProvider.class);
        if (!keyMetricProvider.isAccessible()) {
            throw new IllegalArgumentException("Cannot access provider");
        }

        return keyMetricProvider.resolveKeyMetric(targetName, metricName);
    }

    /**
     * Fetches all available charts for the given target.
     * <p>
     * Note that this only loads the {@link MetricDescription} not the actual data, so this call should be quite fast.
     *
     * @param target the target to fetch charts for
     * @return the list of available charts as descriptors
     */
    public List<MetricDescription> fetchAllCharts(Object target) {
        String targetName = determineTargetName(target);
        List<MetricDescription> result = new ArrayList<>();
        chartProviders.getParts().stream().filter(ChartProvider::isAccessible).forEach(provider -> {
            provider.collectCharts(targetName, description -> {
                description.targetName = targetName;
                description.providerName = provider.getName();
                result.add(description);
            });
        });

        result.sort(Comparator.comparing(MetricDescription::getPriority));

        return result;
    }

    /**
     * Resolves the chart based on the given parameters.
     *
     * @param providerName the name of the {@link ChartProvider}
     * @param targetName   the target to fetch the metric for
     * @param metricName   the name of the chart in case the provider has several
     * @return the resolved and computed chart
     */
    public Chart resolveChart(String providerName, String targetName, String metricName) {
        ChartProvider chartProvider = globalContext.findPart(providerName, ChartProvider.class);
        if (!chartProvider.isAccessible()) {
            throw new IllegalArgumentException("Cannot access provider");
        }

        return chartProvider.resolveChart(targetName, metricName);
    }
}
