/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.biz.analytics.charts.Dataset;
import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.Consumer;

@Register
public class UAChartProvider implements ChartProvider<SQLUserAccount> {
    @Override
    public Class<SQLUserAccount> getTargetType() {
        return SQLUserAccount.class;
    }

    @Override
    public void collectCharts(SQLUserAccount target, Consumer<MetricDescription> descriptionConsumer) {
        descriptionConsumer.accept(new MetricDescription(target, "activity").markImportant());
    }

    @Override
    public Chart resolveChart(String targetName, String chartName) {
        return new LineChart("activity").withLabels(Arrays.asList("A", "B", "C"))
                                        .addDataset(new Dataset("test").addValue(1).addValue(2).addValue(3));
    }

    @Nonnull
    @Override
    public String getName() {
        return "activity";
    }
}
