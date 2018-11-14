/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Register(classes = JobFactory.class)
public class ExamplePolarAreaChart extends PolarAreaChartJobFactory {

    @Nonnull
    @Override
    public String getName() {
        return "polarbear";
    }

    @Override
    protected void computeChartData(Map<String, String> context,
                                    BiConsumer<String, Number> valueConsumer,
                                    BiConsumer<String, Cell> additionalMetrics) {
        valueConsumer.accept("A", 30);
        valueConsumer.accept("B", 60);
        valueConsumer.accept("C", 90);

        additionalMetrics.accept("Test", cells.of(42));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {

    }

    @Override
    public String getCategory() {
        return "report";
    }
}
