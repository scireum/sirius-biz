/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Reports all rate limit events.
 * <p>
 * Note that this is filtered per tenant (except for the system tenant).
 * <p>
 * This job will also offer itself as matching job for all entities which implement {@link RateLimitedEntity}.
 */
@Register(framework = Isenguard.FRAMEWORK_ISENGUARD)
@Permission(Isenguard.PERMISSION_VIEW_RATE_LIMITS)
public class RateLimitEventsReportJobFactory extends ReportJobFactory {

    @Part
    private OMA oma;

    private static final Parameter<String> SCOPE_PARAMETER = new StringParameter("scope", "Scope").hidden().build();

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return targetObject instanceof RateLimitedEntity;
    }

    @Override
    protected void computePresetFor(QueryString queryString, Object targetObject, Map<String, Object> preset) {
        preset.put(SCOPE_PARAMETER.getName(), ((RateLimitedEntity) targetObject).getRateLimitScope());
    }

    @Override
    public String getCurrentLabel(Map<String, String> context) {
        if (SCOPE_PARAMETER.get(context).isPresent()) {
            return NLS.get("RateLimitEventsReportJobFactory.label.filtered");
        }

        return super.getCurrentLabel(context);
    }

    @Override
    public String getCurrentDescription(Map<String, String> context) {
        if (SCOPE_PARAMETER.get(context).isPresent()) {
            return NLS.get("RateLimitEventsReportJobFactory.description.filtered");
        }

        return super.getCurrentDescription(context);
    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) {
        SmartQuery<RateLimitingTriggeredEvent> query = oma.select(RateLimitingTriggeredEvent.class)
                                                          .eqIgnoreNull(RateLimitingTriggeredEvent.SCOPE,
                                                                        SCOPE_PARAMETER.get(context).orElse(null));
        if (!UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)) {
            query.eq(RateLimitingTriggeredEvent.TENANT, UserContext.getCurrentUser().getTenantId());
        }

        report.addColumn("timestamp", "Timestamp");
        report.addColumn("realm", "Realm");
        report.addColumn("scope", "Scope");

        if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)) {
            report.addColumn("tenant", "Tenant");
        }

        report.addColumn("limit", "Limit");
        report.addColumn("location", "Location");

        for (RateLimitingTriggeredEvent event : query.limit(250)
                                                     .orderDesc(RateLimitingTriggeredEvent.EVENT_DATE)
                                                     .orderDesc(RateLimitingTriggeredEvent.EVENT_TIMESTAMP)
                                                     .queryList()) {
            List<Cell> values = new ArrayList<>();
            values.add(cells.of(event.getEventTimestamp()));
            values.add(cells.of(event.getRealm()));
            values.add(cells.of(event.getScope()));
            if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)) {
                values.add(cells.of(event.getTenant()));
            }
            values.add(cells.rightAligned(event.getLimit()));
            values.add(cells.of(event.getLocation()));
            report.addCells(values);
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(SCOPE_PARAMETER);
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Nonnull
    @Override
    public String getName() {
        return "rate-limit-events-report";
    }
}
