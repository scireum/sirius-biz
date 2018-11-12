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
import sirius.biz.jobs.JobFactory;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Register(classes = {JobFactory.class, TimeseriesChartJobFactory.class})
public class ExampleLineChart extends TimeseriesChartJobFactory {

    @Override
    public void provideData(Timeseries timeseries,
                            Map<String, String> context,
                            Dataset dataset,
                            Optional<BiConsumer<String, Object>> additionalMetrics) {
        timeseries.getIntervals().forEach(i -> dataset.addValue(i.getStart().getYear()));
    }

    @Nonnull
    @Override
    public String getName() {
        return "lines";
    }
}
