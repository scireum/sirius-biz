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
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lists all currently blocked IP addresses as reported by {@link Isenguard#getBlockedIPs()}.
 */
@Register(framework = Isenguard.FRAMEWORK_ISENGUARD)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class BlockedIPsReportJobFactory extends ReportJobFactory {

    @Part
    private Isenguard isenguard;

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) {
        report.addColumn("ip", "IP");

        for (String ip : isenguard.getBlockedIPs()) {
            report.addCells(Collections.singletonList(cells.of(ip)));
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // This job has no parameters...
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Nonnull
    @Override
    public String getName() {
        return "blocked-ips-report";
    }

    @Override
    public int getPriority() {
        return 8120;
    }
}
