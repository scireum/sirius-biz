/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.checks;

import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;

/**
 * Provides the batch executor which is used as bridge between the {@link sirius.biz.cluster.work.DistributedTasks}
 * framework and the schedulers of the checks framework.
 * <p>
 * This executor will receive the individual batch descriptions and send them back to the appropriate scheduler
 * to that they can be evaluated and executed.
 *
 * @see SQLDailyCheckScheduler
 * @see SQLChangeCheckScheduler
 * @see MongoDailyCheckScheduler
 * @see MongoChangeCheckScheduler
 */
public class CheckBatchExecutor extends AnalyticsBatchExecutor {

    @Override
    public String queueName() {
        return CheckSchedulerExecutor.QUEUE_CHECKS;
    }
}
