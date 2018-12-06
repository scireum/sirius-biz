/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Register(classes = JobFactory.class)
public class ExampleReport extends ReportJobFactory {

    @Nonnull
    @Override
    public String getName() {
        return "report";
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {

    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return false;
    }

    @Override
    protected void computePresetFor(Object targetObject, Map<String, Object> preset) {

    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) {
        report.addColumn("A", "A");
        report.addColumn("B", "B");
        report.addColumn("C", "C");

        additionalMetricConsumer.accept("Test",
                                        cells.valueAndTrend(Amount.of(11),
                                                            Amount.of(10),
                                                            NumberFormat.TWO_DECIMAL_PLACES));
        additionalMetricConsumer.accept("Test1",
                                        cells.sparkline(Amount.TEN,
                                                        NumberFormat.TWO_DECIMAL_PLACES,
                                                        Arrays.asList(Amount.of(1),
                                                                      Amount.of(4),
                                                                      Amount.of(-3.234),
                                                                      Amount.of(4),
                                                                      Amount.of(-3.234),
                                                                      Amount.of(4),
                                                                      Amount.of(-3.234),
                                                                      Amount.of(4),
                                                                      Amount.of(-3.234),
                                                                      Amount.of(4),
                                                                      Amount.of(-3.234),
                                                                      Amount.of(2.45245))));

        report.addCells(Arrays.asList(cells.trend(Amount.of(10), Amount.of(11), NumberFormat.TWO_DECIMAL_PLACES),
                                      cells.of("X"),
                                      cells.link(LocalDateTime.now(), "/ps")));
        report.addCells(Arrays.asList(cells.valueAndTrend(Amount.of(11),
                                                          Amount.of(10),
                                                          NumberFormat.TWO_DECIMAL_PLACES),
                                      cells.of("Y"),
                                      cells.of(LocalDateTime.now())));
        report.addCells(Arrays.asList(cells.valueAndTrendIcon(Amount.of(12),
                                                              Amount.of(11),
                                                              NumberFormat.TWO_DECIMAL_PLACES),
                                      cells.of("Z"),
                                      cells.of(LocalDateTime.now())));
    }

    @Override
    public String getCategory() {
        return "import";
    }
}
