/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Priorized;

public class MetricDescription {

    private String providerName;
    private String targetEntityName;
    private String metricName;
    private int priority = Priorized.DEFAULT_PRIORITY;
    private boolean important;

    public MetricDescription(String targetEntityName, String metricName) {
        this.targetEntityName = targetEntityName;
        this.metricName = metricName;
    }

    public MetricDescription(BaseEntity<?> targetEntity, String metricName) {
        this(targetEntity.getUniqueName(), metricName);
    }

    public MetricDescription(String targetEntityName) {
        this(targetEntityName, null);
    }

    public MetricDescription(BaseEntity<?> targetEntity) {
        this(targetEntity.getUniqueName(), null);
    }

    protected MetricDescription forMetricProvider(Named provider) {
        this.providerName = provider.getName();
        return this;
    }

    public MetricDescription withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public MetricDescription markImportant() {
        this.important = true;
        return this;
    }

    public  JSONObject asJSON() {
        JSONObject result = new JSONObject();
        result.put("provider", providerName);
        result.put("target", targetEntityName);
        result.put("metric", metricName);

        return result;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getTargetEntityName() {
        return targetEntityName;
    }

    public String getMetricName() {
        return metricName;
    }

    public int getPriority() {
        return priority;
    }



    public boolean isImportant() {
        return important;
    }

}
