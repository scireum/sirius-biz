/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.JobCategory;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link ImportBatchProcessTaskExecutor}.
 */
public abstract class ImportBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the priorized queue named "import-jobs".
     * <p>
     * This should be used for jobs in {@link sirius.biz.jobs.JobCategory#CATEGORY_IMPORT}.
     */
    public static class ImportBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "import-jobs";
        }
    }

    @Override
    protected abstract ImportJob createJob(ProcessContext process);

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return ImportBatchProcessTaskExecutor.class;
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_IMPORT;
    }

    @Override
    public String getIcon() {
        return "fa fa-upload";
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.SIX_YEARS;
    }

}
