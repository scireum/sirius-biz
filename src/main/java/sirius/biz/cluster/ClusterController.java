/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.cluster.work.DistributedTasks;
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
import sirius.kernel.info.Product;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.Routed;
import sirius.web.health.Cluster;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
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
public class ClusterController extends BasicController {

    /**
     * Names the permissions required to view and manage the cluster state.
     */
    public static final String PERMISSION_SYSTEM_CLUSTER = "permission-system-cluster";

    public static final String RESPONSE_NAME = "name";
    public static final String RESPONSE_LABEL = "label";
    public static final String RESPONSE_CODE = "code";
    public static final String RESPONSE_DESCRIPTION = "description";
    public static final String RESPONSE_NODE_STATE = "nodeState";
    public static final String RESPONSE_VERSION = "version";
    public static final String RESPONSE_DETAILED_VERSION = "detailedVersion";
    public static final String RESPONSE_UPTIME = "uptime";
    public static final String RESPONSE_BLEEDING = "bleeding";
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
    @Nullable
    private Locks locks;

    @Part
    private DistributedTasks distributedTasks;

    @Override
    public void onError(WebContext webContext, HandledException error) {
        webContext.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, error);
    }

    /**
     * Reports all metrics of this node as JSON.
     *
     * @param webContext the request to handle
     * @param out        the output to write the JSON to
     * @param token      the cluster authentication token
     */
    @Routed( "/system/cluster/state/:1")
    @InternalService
    public void nodeInfo(WebContext webContext, JSONStructuredOutput out, String token) {
        if (!clusterManager.isClusterAPIToken(token)) {
            webContext.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

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
     * @param webContext the request to handle
     * @param out        the output to write the JSON to
     * @param token      the cluster authentication token
     */
    @Routed("/system/cluster/background/:1")
    @InternalService
    public void backgroundInfo(WebContext webContext, JSONStructuredOutput out, String token) {
        if (!clusterManager.isClusterAPIToken(token)) {
            webContext.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        out.property(RESPONSE_NAME, CallContext.getNodeName());
        out.property(RESPONSE_NODE_STATE, cluster.getNodeState().toString());
        out.property(RESPONSE_VERSION, Product.getProduct().getVersion());
        out.property(RESPONSE_DETAILED_VERSION, Product.getProduct().getDetails());
        out.property(RESPONSE_UPTIME, NLS.convertDuration(Sirius.getUptimeInMilliseconds(), true, false));
        out.property(RESPONSE_BLEEDING, neighborhoodWatch.isBleeding());

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
     * @param webContext the request to handle
     */
    @Routed("/system/cluster")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void cluster(WebContext webContext) {
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
        webContext.respondWith()
                  .template("/templates/biz/cluster/cluster.html.pasta", jobKeys, descriptions, clusterInfo, locks);
    }

    /**
     * Removes a node from the cluster member set.
     * <p>
     * Note that this should only be used to remove a non-existing node as an active node will be re-discovered
     * almost instantly.
     *
     * @param webContext the request to handle
     * @param node       the node to remove
     */
    @Routed("/system/cluster/kill/:1")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void kill(WebContext webContext, String node) {
        clusterManager.killNode(node);
        waitAndRedirectToClusterUI(webContext);
    }

    protected void waitAndRedirectToClusterUI(WebContext webContext) {
        webContext.markAsLongCall();
        delayLine.callDelayed(Tasks.DEFAULT, 2, () -> webContext.respondWith().redirectToGet("/system/cluster"));
    }

    /**
     * Enables or disables bleeding out a node (disabling all upcoming background activities).
     *
     * @param webContext the request to handle
     * @param setting    either "enable" or "disable" to control what to do
     * @param node       the node to change the setting for
     * @see NeighborhoodWatch#changeBleeding(String, boolean)
     */
    @Routed("/system/cluster/bleed/:1/:2")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void bleed(WebContext webContext, String setting, String node) {
        neighborhoodWatch.changeBleeding(node, FLAG_ENABLE.equals(setting));
        waitAndRedirectToClusterUI(webContext);
    }

    /**
     * Provides an API to enable or disable bleeding out a node (disabling all upcoming background activities).
     *
     * @param webContext the request to handle
     * @param setting    either "enable" or "disable" to control what to do
     * @param node       the node to change the setting for
     * @param token      the security token (verified against {@link InterconnectClusterManager#getClusterAPIToken()})
     * @see NeighborhoodWatch#changeBleeding(String, boolean)
     */
    @Routed("/system/cluster/bleed/:1/:2/:3")
    public void apiBleed(WebContext webContext, String setting, String node, String token) {
        if (!clusterManager.isClusterAPIToken(token)) {
            webContext.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        neighborhoodWatch.changeBleeding(node, FLAG_ENABLE.equals(setting));
        waitAndRedirectToClusterUI(webContext);
    }

    /**
     * Determines if the system is ready for processing requests.
     * <p>
     * This can be used as health check (e.g. for ha_proxy or varnish). This will return 200 OK as soon
     * as the node has fully started up and start failing (with a 503 SERVICE UNAVAILABLE) as soon as bleeding out
     * is started.
     *
     * @param ctx the request to handle
     * @see NeighborhoodWatch#changeBleeding(String, boolean)
     */
    @Routed("/system/cluster/ready")
    public void ready(WebContext ctx) {
        if (Sirius.isRunning() && !neighborhoodWatch.isBleeding()) {
            ctx.respondWith().direct(HttpResponseStatus.OK, "OK");
        } else {
            ctx.respondWith()
               .error(HttpResponseStatus.SERVICE_UNAVAILABLE, "Service not fully started or bleeding out...");
        }
    }

    /**
     * Determines if the system is fully bled out and can be stopped.
     * <p>
     * Returns a 200 OK if the system can (most probably) be restarted or a 417 EXPECTATION FAILED otherwise.
     *
     * @param webContext the request to handle
     * @see NeighborhoodWatch#changeBleeding(String, boolean)
     */
    @Routed("/system/cluster/halted")
    public void halted(WebContext webContext) {
        if (!Sirius.isRunning() || (neighborhoodWatch.isBleeding() && distributedTasks.getNumberOfActiveTasks() == 0)) {
            webContext.respondWith().direct(HttpResponseStatus.OK, "OK");
        } else {
            webContext.respondWith()
                      .direct(HttpResponseStatus.EXPECTATION_FAILED,
                              "Tasks running: " + distributedTasks.getNumberOfActiveTasks());
        }
    }

    /**
     * Enables or disables a background job globally.
     *
     * @param webContext the request to handle
     * @param setting    either "enable" or "disable" to control what to do
     * @param jobKey     the jobKey to change
     */
    @Routed("/system/cluster/global/:1/:2")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void globalSwitch(WebContext webContext, String setting, String jobKey) {
        neighborhoodWatch.changeGlobalEnabledFlag(jobKey, FLAG_ENABLE.equals(setting));
        waitAndRedirectToClusterUI(webContext);
    }

    /**
     * Enables or disables a background job globally.
     *
     * @param webContext the request to handle
     * @param setting    either "enable" or "disable" to control what to do
     * @param node       the node to change the setting for
     * @param jobKey     the jobKey to change
     */
    @Routed("/system/cluster/local/:1/:2/:3")
    @Permission(PERMISSION_SYSTEM_CLUSTER)
    public void localSwitch(WebContext webContext, String setting, String node, String jobKey) {
        neighborhoodWatch.changeLocalOverwrite(node, jobKey, FLAG_DISABLE.equals(setting));
        waitAndRedirectToClusterUI(webContext);
    }
}
