/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.locks.Locks;
import sirius.kernel.Sirius;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.DelayLine;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.metrics.Metric;
import sirius.kernel.health.metrics.Metrics;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.health.Cluster;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.JSONStructuredOutput;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Responsible for cluster intercommunication and reporting.
 * <p>
 * Cluster members collect certain metrics and background infos via JSON using this controller.
 * <p>
 * Also this is resonsible for rendering the <tt>/system/cluster</tt> view.
 */
@Register
public class ClusterController implements Controller {

    /**
     * Names the permissions required to view and manage the cluster state.
     */
    public static final String PERMISSION_SYSTEM_CLUSTER = "permission-system-cluster";

    public static final String RESPONSE_NAME = "name";
    public static final String RESPONSE_LABEL = "label";
    public static final String RESPONSE_CODE = "code";
    public static final String RESPONSE_DESCRIPTION = "description";
    public static final String RESPONSE_NODE_STATE = "nodeState";
    public static final String RESPONSE_UPTIME = "uptime";
    public static final String RESPONSE_METRICS = "metrics";
    public static final String RESPONSE_METRIC = "metric";
    public static final String RESPONSE_VALUE = "value";
    public static final String RESPONSE_UNIT = "unit";
    public static final String RESPONSE_STATE = "state";
    public static final String RESPONSE_JOBS = "jobs";
    public static final String RESPONSE_JOB = "job";
    public static final String RESPONSE_LOCAL = "local";
    public static final String RESPONSE_LOCAL_OVERWRITE = "localOverwrite";
    public static final String RESPONSE_GLOBALLY_ENABLED = "globallyEnabled";
    public static final String RESPONSE_EXECUTION_INFO = "executionInfo";
    public static final String FLAG_ENABLE = "enable";
    public static final String FLAG_DISABLE = "disable";

    @Part
    private Cluster cluster;

    @Part
    private Metrics metrics;

    @Part
    private NeighborhoodWatch neighborhoodWatch;

    @Part
    private InterconnectClusterManager clusterManager;

    @Part
    private DelayLine delayLine;

    @Part
    private Locks locks;

    @Override
    public void onError(WebContext ctx, HandledException error) {
        ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, error);
    }

    /**
     * Reports all metrics of this node as JSON.
     *
     * @param ctx the request to handle
     * @param out the output to write the JSON to
     */
    @Routed(value = "/system/cluster/state", jsonCall = true)
    public void nodeInfo(WebContext ctx, JSONStructuredOutput out) {
        out.property(RESPONSE_NAME, CallContext.getNodeName());
        out.property(RESPONSE_NODE_STATE, cluster.getNodeState().toString());
        out.property(RESPONSE_UPTIME, NLS.convertDuration(Sirius.getUptimeInMilliseconds(), true, false));

        out.beginArray(RESPONSE_METRICS);
        for (Metric m : metrics.getMetrics()) {
            out.beginObject(RESPONSE_METRIC);
            out.property(RESPONSE_CODE, m.getCode());
            out.property(RESPONSE_LABEL, m.getLabel());
            out.property(RESPONSE_VALUE, m.getValue());
            out.property(RESPONSE_UNIT, m.getUnit());
            out.property(RESPONSE_STATE, m.getState().name());
            out.endObject();
        }
        out.endArray();
    }

    /**
     * Reports all background activities of this node as JSON.
     *
     * @param ctx the request to handle
     * @param out the output to write the JSON to
     */
    @Routed(value = "/system/cluster/background", jsonCall = true)
    public void backgroundInfo(WebContext ctx, JSONStructuredOutput out) {
        out.property(RESPONSE_NAME, CallContext.getNodeName());
        out.property(RESPONSE_NODE_STATE, cluster.getNodeState().toString());
        out.property(RESPONSE_UPTIME, NLS.convertDuration(Sirius.getUptimeInMilliseconds(), true, false));

        out.beginArray(RESPONSE_JOBS);
        for (BackgroundJobInfo job : neighborhoodWatch.getLocalBackgroundInfo().getJobs().values()) {
            out.beginObject(RESPONSE_JOB);
            out.property(RESPONSE_NAME, job.getName());
            out.property(RESPONSE_DESCRIPTION, job.getDescription());
            out.property(RESPONSE_LOCAL, job.getSynchronizeType().name());
            out.property(RESPONSE_LOCAL_OVERWRITE, job.isLocalOverwrite());
            out.property(RESPONSE_GLOBALLY_ENABLED, job.isGloballyEnabled());
            out.property(RESPONSE_EXECUTION_INFO, job.getExecutionInfo());
            out.endObject();
        }
        out.endArray();
    }

    /**
     * Provides an overview of the cluster, its members and their background activities.
     *
     * @param ctx the request to handle
     */
    @Routed("/system/cluster")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void cluster(WebContext ctx) {
        List<BackgroundInfo> clusterInfo = neighborhoodWatch.getClusterBackgroundInfo();
        List<String> jobKeys = clusterInfo.stream()
                                          .flatMap(node -> node.getJobs().keySet().stream())
                                          .distinct()
                                          .sorted(Comparator.naturalOrder())
                                          .collect(Collectors.toList());
        Map<String, String> descriptions = clusterInfo.stream()
                                                      .flatMap(node -> node.getJobs().entrySet().stream())
                                                      .collect(Collectors.toMap(Map.Entry::getKey,
                                                                                e -> e.getValue().getDescription(),
                                                                                (a, b) -> a));
        ctx.respondWith()
           .template("templates/cluster/cluster.html.pasta", jobKeys, descriptions, clusterInfo, locks);
    }

    /**
     * Enables or disables a background job globally.
     *
     * @param ctx     the request to handle
     * @param setting either "enable" or "disable" to control what to do
     * @param jobKey  the jobKey to change
     */
    @Routed("/system/cluster/global/:1/:2")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void globalSwitch(WebContext ctx, String setting, String jobKey) {
        neighborhoodWatch.changeGlobalEnabledFlag(jobKey, FLAG_ENABLE.equals(setting));
        delayLine.callDelayed(Tasks.DEFAULT, 2, () -> ctx.respondWith().redirectTemporarily("/system/cluster"));
    }

    /**
     * Enables or disables a background job globally.
     *
     * @param ctx     the request to handle
     * @param setting either "enable" or "disable" to control what to do
     * @param node    the node to change the setting for
     * @param jobKey  the jobKey to change
     */
    @Routed("/system/cluster/local/:1/:2/:3")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void localSwitch(WebContext ctx, String setting, String node, String jobKey) {
        neighborhoodWatch.changeLocalOverwrite(node, jobKey, FLAG_DISABLE.equals(setting));
        delayLine.callDelayed(Tasks.DEFAULT, 2, () -> ctx.respondWith().redirectTemporarily("/system/cluster"));
    }
}
