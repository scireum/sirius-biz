/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.analytics.reports.Cells;
import sirius.biz.process.PersistencePeriod;
import sirius.kernel.di.std.Part;

/**
 * Provides a base factory for very simple batch reports.
 * <p>
 * This is intended for reports which do not need to wrap the execution in a {@link BatchJob}
 * (as {@link ReportBatchProcessFactory} does).
 */
public abstract class SimpleReportBatchProcessFactory extends SimpleBatchProcessJobFactory {

    @Part
    protected Cells cells;

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Override
    public String getIcon() {
        return "fa-solid fa-chart-line";
    }
}
