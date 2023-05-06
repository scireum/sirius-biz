/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.charts.ComparisonPeriod;
import sirius.biz.analytics.charts.Timeseries;
import sirius.biz.analytics.charts.Unit;
import sirius.biz.analytics.explorer.DataExplorerController;
import sirius.biz.analytics.metrics.Dataset;
import sirius.biz.analytics.reports.Cell;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.LocalDateParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.kernel.nls.NLS;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Renders a line chart representing a user defined {@link TimeseriesDataProvider timeseries}.
 *
 * @deprecated Use the {@link DataExplorerController Data-Explorer} for advanced
 * charts and statistics.
 */
@Deprecated
public abstract class TimeseriesChartJobFactory extends LinearChartJobFactory implements TimeseriesDataProvider {

    /**
     * Determines the start of the timeseries.
     */
    public static final Parameter<LocalDate> PARAM_START =
            new LocalDateParameter("start", "$TimeseriesDataProvider.start").withDefault(() -> LocalDate.now()
                                                                                                        .minusDays(30))
                                                                            .build();

    /**
     * Determines the end of the timeseries.
     */
    public static final Parameter<LocalDate> PARAM_END =
            new LocalDateParameter("end", "$TimeseriesDataProvider.end").withDefault(() -> LocalDate.now().minusDays(1))
                                                                        .build();

    /**
     * Determines the desired unit or resolution of the timeseries.
     */
    public static final Parameter<Unit> PARAM_UNIT =
            new EnumParameter<>("unit", "$TimeseriesDataProvider.unit", Unit.class).build();

    /**
     * Determines the desired comparison period for the timeseries.
     */
    public static final Parameter<ComparisonPeriod> PARAM_COMPARISON_PERIOD = new EnumParameter<>("comparisonPeriod",
                                                                                                  "$TimeSeriesChartJobFactory.comparisonPeriod",
                                                                                                  ComparisonPeriod.class).build();

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(PARAM_START);
        parameterCollector.accept(PARAM_END);
        parameterCollector.accept(PARAM_UNIT);
        parameterCollector.accept(PARAM_COMPARISON_PERIOD);
    }

    @Override
    protected void computeChartData(Map<String, String> context,
                                    Consumer<List<String>> labelConsumer,
                                    Consumer<Dataset> datasetConsumer,
                                    BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        LocalDate start = PARAM_START.require(context);
        LocalDate end = PARAM_END.require(context);
        Unit unit = PARAM_UNIT.get(context).orElse(null);

        Timeseries timeseries = new Timeseries(start.atStartOfDay(), end.atStartOfDay().plusDays(1), unit, 35, 50);
        labelConsumer.accept(timeseries.getLabels());

        Dataset currentDataset = new Dataset(NLS.get("TimeSeriesChartJobFactory.currentPeriod"));
        provideData(timeseries, context, currentDataset, Optional.of(additionalMetricConsumer));
        datasetConsumer.accept(currentDataset);

        ComparisonPeriod period = PARAM_COMPARISON_PERIOD.get(context).orElse(null);
        if (period != null) {
            Dataset comparedDataset = new Dataset(NLS.get("TimeSeriesChartJobFactory.comparisonPeriod"));
            provideData(timeseries.computeComparisonPeriod(period), context, comparedDataset, Optional.empty());
            datasetConsumer.accept(comparedDataset);
        }
    }
}
