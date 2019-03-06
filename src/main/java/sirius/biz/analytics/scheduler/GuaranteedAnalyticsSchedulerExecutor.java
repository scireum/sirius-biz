/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

/**
 * Provides the executor used to execute {@link AnalyticsScheduler schedulers} which use <b>guaranteed execution</b>
 * scheduling.
 */
public class GuaranteedAnalyticsSchedulerExecutor extends AnalyticsSchedulerExecutor {

    @Override
    public String queueName() {
        return AnalyticalEngine.QUEUE_ANALYTICS_SCHEDULER;
    }
}
