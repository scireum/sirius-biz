/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.insights;

import java.util.List;

public interface Insights {

    List<Insight> fetchOpenInsights();

    InsightBuilder createInsight(String tenantId, String userId);

    InsightBuilder createInsight();
}
