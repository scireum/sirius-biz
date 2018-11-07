/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.charts.ComparisonPeriod;
import sirius.biz.analytics.charts.Dataset;
import sirius.biz.analytics.charts.Timeseries;
import sirius.biz.analytics.charts.TimeseriesDataProvider;
import sirius.biz.analytics.charts.Unit;
import sirius.biz.params.EnumParameter;
import sirius.biz.params.Parameter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class TimeseriesChartJobFactory extends LinearChartJobFactory implements TimeseriesDataProvider {

    private static final EnumParameter<ComparisonPeriod> PARAM_COMPARISON_PERIOD = new EnumParameter<>(
            "comparisonPeriod",
            "TimeseriesChartJobFactory.comparisonPeriod",
            ComparisonPeriod.class).withSpan(12, 12);

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(TimeseriesDataProvider.PARAM_START);
        parameterCollector.accept(TimeseriesDataProvider.PARAM_END);
        parameterCollector.accept(TimeseriesDataProvider.PARAM_UNIT);
        parameterCollector.accept(PARAM_COMPARISON_PERIOD);
    }

    @Override
    protected void computeChartData(Map<String, String> context,
                                    Consumer<List<String>> labelConsumer,
                                    Consumer<Dataset> datasetConsumer) {
        LocalDate start = TimeseriesDataProvider.PARAM_START.require(context);
        LocalDate end = TimeseriesDataProvider.PARAM_END.require(context);
        Unit unit = TimeseriesDataProvider.PARAM_UNIT.get(context).orElse(null);

        Timeseries timeseries = new Timeseries(start.atStartOfDay(), end.atStartOfDay().plusDays(1), unit, 35, 50);
        labelConsumer.accept(timeseries.getLabels());

        Dataset currentDataset = new Dataset("Aktueller Zeitraum");
        provideData(timeseries, context, currentDataset);
        datasetConsumer.accept(currentDataset);

        ComparisonPeriod period = PARAM_COMPARISON_PERIOD.get(context).orElse(null);
        if (period != null) {
            Dataset comparedDataset = new Dataset("Vergleichszeitraum");
            provideData(timeseries.computeComparisonPeriod(period), context, comparedDataset);
            datasetConsumer.accept(comparedDataset);
        }
    }
}
