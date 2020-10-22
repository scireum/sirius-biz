/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Register
public class KeyMetricPro implements KeyMetricProvider<SQLUserAccount> {

    @Override
    public Class<SQLUserAccount> getTargetType() {
        return SQLUserAccount.class;
    }

    @Override
    public void collectKeyMetrics(SQLUserAccount target, Consumer<MetricDescription> descriptionConsumer) {
        descriptionConsumer.accept(new MetricDescription(target, "activity").markImportant());
    }

    @Override
    public KeyMetric resolveKeyMetric(String targetName, String metricName) {
        return new KeyMetric("Aktivität", "90%", "Schnitzel ist lecker");
    }

    @Nonnull
    @Override
    public String getName() {
        return "sqluseraccount-activity";
    }

}
