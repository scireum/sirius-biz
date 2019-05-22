/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler.jdbc;

import sirius.biz.jobs.scheduler.JobConfigData;
import sirius.biz.jobs.scheduler.SchedulerController;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;

/**
 * Provides the controller for managing the SQL based scheduler.
 */
@Register(classes = Controller.class, framework = SQLSchedulerController.FRAMEWORK_SCHEDULER_JDBC)
public class SQLSchedulerController extends SchedulerController<SQLSchedulerEntry> {

    /**
     * Names the framework which must be enabled to activate the scheduling for SQL/JDBC based entries.
     */
    public static final String FRAMEWORK_SCHEDULER_JDBC = "biz.scheduler-jdbc";

    @Override
    protected Class<SQLSchedulerEntry> getEntryType() {
        return SQLSchedulerEntry.class;
    }

    @Override
    protected BasePageHelper<SQLSchedulerEntry, ?, ?, ?> getEntriesAsPage() {
        return SQLPageHelper.withQuery(tenants.forCurrentTenant(oma.select(SQLSchedulerEntry.class)
                                                                   .orderAsc(SQLSchedulerEntry.JOB_CONFIG_DATA.inner(
                                                                           JobConfigData.JOB))));
    }
}
