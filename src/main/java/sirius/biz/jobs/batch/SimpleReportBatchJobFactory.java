/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.jobs.JobCategory;
import sirius.biz.process.PersistencePeriod;

/**
 * Provides a base factory for very simple batch reports, which do not need to wrap the execution in a {@link BatchJob}
 * but rather execute in a single method.
 * <p>
 * This factory is based on {@link SimpleBatchProcessJobFactory}
 */
public abstract class SimpleReportBatchJobFactory extends SimpleBatchProcessJobFactory {

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_REPORT;
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Override
    public String getIcon() {
        return "fa-line-chart";
    }
}
