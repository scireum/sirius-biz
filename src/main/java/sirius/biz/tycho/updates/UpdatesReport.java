/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lists recently clicked updates of the {@link UpdateManager}.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class UpdatesReport extends ReportJobFactory {

    private static final String COLUMN_GUID = "guid";
    private static final String COLUMN_TOTAL = "total";
    private static final String COLUMN_LOGGED_IN = "loggedIn";
    private static final String COLUMN_ANONYMOUS = "anonymous";

    @Part
    private EventRecorder eventRecorder;

    @Part
    private UpdateManager updateManager;

    @Override
    public boolean isAccessibleToCurrentUser() {
        return super.isAccessibleToCurrentUser() && updateManager.hasFeedUrl();
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // This report has no parameters...
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        report.addColumn(COLUMN_GUID, "$UpdatesReport.guid");
        report.addColumn(COLUMN_TOTAL, "$UpdatesReport.total");
        report.addColumn(COLUMN_LOGGED_IN, "$UpdatesReport.loggedIn");
        report.addColumn(COLUMN_ANONYMOUS, "$UpdatesReport.anonymous");

        LocalDate start = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        additionalMetricConsumer.accept(NLS.get("UpdatesReport.start"), cells.of(start));
        additionalMetricConsumer.accept(NLS.get("UpdatesReport.end"), cells.of(LocalDate.now()));

        AtomicInteger totalClicks = new AtomicInteger();
        eventRecorder.createQuery(
                //language=SQL
                """
                        SELECT count(*) AS all,
                               countIf(userData_userId IS NOT NULL) AS loggedIn,
                               countIf(userData_userId IS NULL) AS anonymous,
                               updateGuid
                        FROM updateclickevent
                        WHERE eventDate >= ${start}
                          AND eventDate <= ${end}
                        GROUP BY updateGuid
                        ORDER BY count(*) desc
                        """).set("start", start).set("end", LocalDate.now()).iterateAll(row -> {
            String guid = row.getValue("updateGuid").asString();
            report.addRow(List.of(Tuple.create(COLUMN_GUID,
                                               Urls.isHttpUrl(guid) ? cells.link(guid, guid, true) : cells.of(guid)),
                                  Tuple.create(COLUMN_TOTAL, cells.of(row.getValue("all").asInt(0))),
                                  Tuple.create(COLUMN_LOGGED_IN, cells.of(row.getValue(COLUMN_LOGGED_IN).asInt(0))),
                                  Tuple.create(COLUMN_ANONYMOUS, cells.of(row.getValue(COLUMN_ANONYMOUS).asInt(0)))));
            totalClicks.addAndGet(row.getValue("all").asInt(0));
        }, Limit.UNLIMITED);

        additionalMetricConsumer.accept(NLS.get("UpdatesReport.totalClicks"), cells.of(totalClicks.get()));
    }

    @Override
    public String getName() {
        return "UpdatesReport";
    }

    @Override
    public int getPriority() {
        return 8300;
    }
}
