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
import sirius.kernel.async.CallContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Reports the rate limits currently being applied.
 * <p>
 * This contains the limits for the <tt>ip</tt>, <tt>user</tt> and (if permitted) <tt>tenant</tt>.
 *
 * @see Isenguard#PERMISSION_VIEW_RATE_LIMITS
 */
@Register(framework = Isenguard.FRAMEWORK_ISENGUARD)
@Permission(UserInfo.PERMISSION_LOGGED_IN)
public class RateLimitReportJobFactory extends ReportJobFactory {

    @Part
    private Isenguard isenguard;

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) {
        report.addColumn("type", "type");
        report.addColumn("realm", "Realm");
        report.addColumn("limit", "Limit");
        reportForIP(report);

        UserInfo user = UserContext.getCurrentUser();
        reportForUser(report, user);
        reportForTenant(report, user);
    }

    private void emitCurrentValue(Report report, String type, String realm, String scope) {
        String currentValue = isenguard.getRateLimitInfo(scope, realm, null);
        if (currentValue != null) {
            report.addCells(Arrays.asList(cells.of(type), cells.of(realm), cells.rightAligned(currentValue)));
        }
    }

    private void reportForIP(Report report) {
        WebContext webContext = WebContext.getCurrent();
        if (webContext.isValid()) {
            String ip = webContext.getRemoteIP().getHostAddress();
            for (String realm : isenguard.getRealmsByType(Isenguard.REALM_TYPE_IP)) {
                emitCurrentValue(report, Isenguard.REALM_TYPE_IP, realm, ip);
            }
        }
    }

    private void reportForUser(Report report, UserInfo user) {
        for (String realm : isenguard.getRealmsByType("user")) {
            emitCurrentValue(report, "user", realm, user.getUserId());
        }
    }

    private void reportForTenant(Report report, UserInfo user) {
        if (user.hasPermission(Isenguard.PERMISSION_VIEW_RATE_LIMITS)) {
            for (String realm : isenguard.getRealmsByType("tenant")) {
                emitCurrentValue(report, "tenant", realm, user.getTenantId());
            }
        }
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Nonnull
    @Override
    public String getName() {
        return "rate-limit-report";
    }

    @Override
    public int getPriority() {
        return 8110;
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // there are no parameters for this job...
    }
}
