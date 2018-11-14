/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.LocalDateParameter;
import sirius.kernel.di.std.Named;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public interface TimeseriesDataProvider extends Named {

    LocalDateParameter PARAM_START = new LocalDateParameter("start",
                                                            "$TimeseriesDataProvider.start",
                                                            () -> LocalDate.now().minusDays(30)).withSpan(12, 12);
    LocalDateParameter PARAM_END =
            new LocalDateParameter("end", "$TimeseriesDataProvider.end", () -> LocalDate.now()).withSpan(12, 12);
    EnumParameter<Unit> PARAM_UNIT =
            new EnumParameter<>("unit", "$TimeseriesDataProvider.unit", Unit.class).withSpan(12, 12);

    void provideData(Timeseries timeseries,
                     Map<String, String> context,
                     Dataset dataset,
                     Optional<BiConsumer<String, Cell>> additionalMetrics);
}
