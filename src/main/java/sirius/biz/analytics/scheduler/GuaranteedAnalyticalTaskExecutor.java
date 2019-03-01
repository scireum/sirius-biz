/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

/**
 * Provides the executor used to execute {@link AnalyticalTask analytical tasks} scheduled by a <b>guaranteed execution</b>
 * {@link AnalyticsScheduler}.
 */
public class GuaranteedAnalyticalTaskExecutor extends AnalyticalTaskExecutor {

    @Override
    public String queueName() {
        return AnalyticalEngine.QUEUE_ANALYTICS_BATCH;
    }
}
