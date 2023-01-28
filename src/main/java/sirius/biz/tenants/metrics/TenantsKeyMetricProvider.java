/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics;

import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.biz.tycho.metrics.EntitiesKeyMetricProvider;
import sirius.biz.tycho.metrics.KeyMetric;
import sirius.biz.tycho.metrics.MetricDescription;
import sirius.db.mixing.Entity;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;

@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class TenantsKeyMetricProvider extends EntitiesKeyMetricProvider {

    @Override
    protected boolean isGloballyVisible() {
        return true;
    }

    @Override
    protected Class<? extends Entity> getTargetType() {
        return SQLTenant.class;
    }

    @Override
    protected void collectKeyMetrics(Supplier<MetricDescription> metricFactory) {
        metricFactory.get().markImportant()
                     .withPriority(501)
                     .withMetricName(GlobalTenantMetricComputer.METRIC_NUM_ACTIVE_TENANTS);
//        descriptionConsumer.accept(new MetricDescription("Aktive Mandanten").markImportant()
//                                                                            .withPriority(501)
//                                                                            .withMetricName(GlobalTenantMetricComputer.METRIC_NUM_ACTIVE_TENANTS));
//
//            descriptionConsumer.accept(new MetricDescription("Aktive Benutzer").markImportant()
//                                                                               .withPriority(502)
//                                                                               .withMetricName(
//                                                                                       SQLTenantGlobalMetricComputer.METRIC_NUM_ACTIVE_USERS));
//            descriptionConsumer.accept(new MetricDescription("Mandanten").markImportant().withPriority(503).withMetricName(SQLTenantGlobalMetricComputer.METRIC_NUM_TENANTS));
    }

    @Override
    protected KeyMetric resolveKeyMetric(String metricName) {
        if (Value.of(metricName)
                 .in(GlobalTenantMetricComputer.METRIC_NUM_TENANTS,
                     GlobalTenantMetricComputer.METRIC_NUM_ACTIVE_TENANTS,
                     GlobalTenantMetricComputer.METRIC_NUM_ACTIVE_USERS)) {
            return new KeyMetric(metrics.query().global().monthly(metricName), null).withHistory(List.of(1,5,6,3,7,3,6,8,6));
        } else {
            return null;
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "tenants-sql";
    }
}
