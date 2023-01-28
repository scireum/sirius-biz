/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.metrics.Metric;
import sirius.kernel.health.metrics.MetricState;
import sirius.web.health.Cluster;
import sirius.web.health.ClusterManager;
import sirius.web.health.NodeInfo;
import sirius.web.http.WebServer;
import sirius.web.services.JSONCall;

import javax.annotation.Nonnull;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Implements a {@link ClusterManager} by discovering and managing nodes via the {@link Interconnect}.
 */
@Register(classes = {InterconnectClusterManager.class, ClusterManager.class, InterconnectHandler.class})
public class InterconnectClusterManager implements ClusterManager, InterconnectHandler {

    private static final String MESSAGE_TYPE = "type";
    private static final String TYPE_PING = "PING";
    private static final String TYPE_PONG = "PONG";
    private static final String TYPE_KILL = "KILL";

    private static final String MESSAGE_NAME = "name";
    private static final String MESSAGE_ADDRESS = "address";

    private static final Duration PING_INTERVAL = Duration.ofMinutes(15);
    private static final int SHORT_CLUSTER_HTTP_TIMEOUT_MILLIS = 1000;

    public static final String RESPONSE_NODE_NAME = "node";
    public static final String RESPONSE_ERROR = "error";
    public static final String RESPONSE_ERROR_MESAGE = "errorMesage";
    private static final int HTTP_DEFAULT_PORT = 80;

    private final Map<String, String> members = new ConcurrentHashMap<>();
    private LocalDateTime lastPing = null;

    @Part
    private Interconnect interconnect;

    @ConfigValue("sirius.clusterToken")
    private String clusterAPIToken;

    @ConfigValue("sirius.nodeAddress")
    private String localNodeAddress;

    @Nonnull
    @Override
    public String getName() {
        return "cluster";
    }

    /**
     * Returns the cluster API token which is used to authenticate nodes against each other and also
     * maintenance workers (e.g. systems which start bleeding of nodes before a system update).
     *
     * @return the cluster token to use
     */
    public String getClusterAPIToken() {
        if (Strings.isEmpty(clusterAPIToken)) {
            clusterAPIToken = Strings.generateCode(32);
        }
        return clusterAPIToken;
    }

    /**
     * Determines if the given token matches the cluster token.
     *
     * @param token the token to check
     * @return <tt>true</tt> if the given token is the cluster token, <tt>false</tt> otherwise
     */
    public boolean isClusterAPIToken(String token) {
        return Strings.areEqual(token, clusterAPIToken);
    }

    protected void sendPing() {
        interconnect.dispatch(getName(), new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_PING));
    }

    @Override
    public void handleEvent(JSONObject event) {
        if (Strings.areEqual(event.getString(MESSAGE_TYPE), TYPE_PING)) {
            lastPing = LocalDateTime.now();
            interconnect.dispatch(getName(),
                                  new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_PONG)
                                                  .fluentPut(MESSAGE_NAME, CallContext.getNodeName())
                                                  .fluentPut(MESSAGE_ADDRESS, getLocalAddress()));
        } else if (Strings.areEqual(event.getString(MESSAGE_TYPE), TYPE_PONG)) {
            String address = event.getString(MESSAGE_ADDRESS);
            if (!Strings.areEqual(address, getLocalAddress()) && Strings.isFilled(address)) {
                String nodeName = event.getString(MESSAGE_NAME);
                if (!Strings.areEqual(members.put(nodeName, address), address)) {
                    Cluster.LOG.INFO("Discovered a new node: %s - %s", nodeName, address);
                }
            }
        } else if (Strings.areEqual(event.getString(MESSAGE_TYPE), TYPE_KILL)) {
            members.remove(event.getString(MESSAGE_NAME));
        }
    }

    private String getLocalAddress() {
        try {
            if (Strings.isEmpty(localNodeAddress)) {
                int port = WebServer.getPort();
                if (port != HTTP_DEFAULT_PORT) {
                    localNodeAddress = "http://" + InetAddress.getLocalHost().getHostAddress() + ":" + port;
                } else {
                    localNodeAddress = "http://" + InetAddress.getLocalHost().getHostAddress();
                }
            }
            return localNodeAddress;
        } catch (UnknownHostException e) {
            return "";
        }
    }

    /**
     * Removes a node as known cluster member.
     * <p>
     * Note that if the node is still alive, it will be re-discovered. Therefore this should only be used to
     * remove nodes which are permanently shut down.
     *
     * @param name the name of the node to remove as member
     */
    public void killNode(String name) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(MESSAGE_TYPE, TYPE_KILL).fluentPut(MESSAGE_NAME, name));
    }

    /**
     * Invokes the URI on each cluster member and returns the received JSON.
     * <p>
     * Note that this will not include the local node.
     *
     * @param uri the uri to invoke
     * @return the JSON per node as stream
     */
    public Stream<JSONObject> callEachNode(String uri) {
        return members.entrySet().stream().map(e -> callNode(e.getKey(), e.getValue(), uri));
    }

    private JSONObject callNode(String nodeName, String endpoint, String uri) {
        try {
            JSONCall call = JSONCall.to(new URI(endpoint + uri));

            // Set short-lived timeouts as we do not want to block a cluster wide query if one node is down...
            call.getOutcall().setConnectTimeout(SHORT_CLUSTER_HTTP_TIMEOUT_MILLIS);
            call.getOutcall().setReadTimeout(SHORT_CLUSTER_HTTP_TIMEOUT_MILLIS);

            JSONObject result = call.getInput();
            result.put(RESPONSE_NODE_NAME, nodeName);
            if (result.getBoolean(RESPONSE_ERROR) == null) {
                result.put(RESPONSE_ERROR, false);
            }

            return result;
        } catch (Exception e) {
            return new JSONObject().fluentPut(RESPONSE_NODE_NAME, nodeName)
                                   .fluentPut(RESPONSE_ERROR, true)
                                   .fluentPut(RESPONSE_ERROR_MESAGE, e.getMessage());
        }
    }

    @Override
    public List<NodeInfo> updateClusterState() {
        if (lastPing == null) {
            Cluster.LOG.INFO("Starting node discovery - I am %s - %s", CallContext.getNodeName(), getLocalAddress());
        }
        if (lastPing == null || Duration.between(lastPing, LocalDateTime.now()).compareTo(PING_INTERVAL) >= 0) {
            sendPing();
        }

        return callEachNode("/system/cluster/state/" + getClusterAPIToken()).map(this::parseNodeState)
                                                                            .sorted(Comparator.comparing(NodeInfo::getName))
                                                                            .toList();
    }

    private NodeInfo parseNodeState(JSONObject response) {
        NodeInfo result = new NodeInfo();
        result.setName(response.getString(RESPONSE_NODE_NAME));
        if (response.getBooleanValue(RESPONSE_ERROR)) {
            result.setNodeState(MetricState.RED);
            return result;
        }

        result.setNodeState(MetricState.valueOf(response.getString(ClusterController.RESPONSE_NODE_STATE)));
        result.setUptime(response.getString(ClusterController.RESPONSE_UPTIME));

        JSONArray nodeMetrics = response.getJSONArray(ClusterController.RESPONSE_METRICS);
        for (int i = 0; i < nodeMetrics.size(); i++) {
            try {
                JSONObject metric = (JSONObject) nodeMetrics.get(i);
                Metric m = new Metric(metric.getString(ClusterController.RESPONSE_CODE),
                                      metric.getString(ClusterController.RESPONSE_LABEL),
                                      metric.getDoubleValue(ClusterController.RESPONSE_VALUE),
                                      MetricState.valueOf(metric.getString(ClusterController.RESPONSE_STATE)),
                                      metric.getString(ClusterController.RESPONSE_UNIT));
                result.getMetrics().add(m);
            } catch (Exception e) {
                Exceptions.handle(Cluster.LOG, e);
            }
        }

        return result;
    }
}
