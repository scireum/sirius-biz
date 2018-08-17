/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.Sirius;
import sirius.kernel.async.CallContext;
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
import sirius.web.services.JSONStructuredOutput;

/**
 * Responsible for cluster intercommunication and reporting.
 * <p>
 * Cluster members collect certain metrics and background infos via JSON using this controller.
 * <p>
 * Also this is resonsible for rendering the <tt>/system/cluster</tt> view.
 */
@Register
public class ClusterController implements Controller {

    public static final String RESPONSE_NAME = "name";
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

    @Part
    private Cluster cluster;
    @Part
    private Metrics metrics;
    @Part
    private NeighborhoodWatch neighborhoodWatch;

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
            out.property(RESPONSE_NAME, m.getName());
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
        for (BackgroundJobInfo job : neighborhoodWatch.getLocalBackgroundInfo().getJobs()) {
            out.beginObject(RESPONSE_JOB);
            out.property(RESPONSE_NAME, job.getName());
            out.property(RESPONSE_LOCAL, job.getLocal().name());
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
    public void cluster(WebContext ctx) {
        ctx.respondWith().template("templates/system/cluter.html.pasta", neighborhoodWatch.getClusterBackgroundInfo());
    }
}
