/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.charts.Dataset;
import sirius.biz.analytics.charts.Timeseries;
import sirius.biz.analytics.reports.Cell;
import sirius.kernel.di.std.Named;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Describes a provider which yields or fills a {@link Dataset dataset} for a given {@link Timeseries timeseries}.
 *
 * @deprecated Use the {@link sirius.biz.analytics.charts.explorer.DataExplorerController Data-Explorer} for advanced
 * charts and statistics.
 */
@Deprecated
public interface TimeseriesDataProvider extends Named {

    /**
     * Fills the given dataset based on the given context and timeseries.
     * <p>
     * If the consumer for additional metrics is present, it can be used to output additional data generated using {@link sirius.biz.analytics.reports.Cells}.
     *
     * @param timeseries        the timeseries describing the data points to compute
     * @param context           the context defining the filters to apply
     * @param dataset           the dataset to populate
     * @param additionalMetrics if present, can be used to output additional metrics like min / max / avg etc.
     * @throws Exception in case of an unexpected error which aborted the computations
     */
    void provideData(Timeseries timeseries,
                     Map<String, String> context,
                     Dataset dataset,
                     Optional<BiConsumer<String, Cell>> additionalMetrics) throws Exception;
}
