/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import com.alibaba.fastjson.JSONObject;

import java.time.LocalDateTime;

public interface MonthlyMetricComputer {

    void compute(LocalDateTime start, LocalDateTime end, JSONObject context);

}
