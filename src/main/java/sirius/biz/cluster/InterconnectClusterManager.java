/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Json;
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
    private static final Duration SHORT_CLUSTER_HTTP_TIMEOUT = Duration.ofSeconds(1);

    public static final String RESPONSE_NODE_NAME = "node";
    public static final String RESPONSE_ERROR = "error";
    private static final String RESPONSE_ERROR_MESSAGE = "errorMessage";
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final String CLIENT_SELECTOR_CLUSTER = "_cluster_";

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
        interconnect.dispatch(getName(), Json.createObject().put(MESSAGE_TYPE, TYPE_PING));
    }

    @Override
    public void handleEvent(ObjectNode event) {
        String messageType = event.path(MESSAGE_TYPE).asText(null);
        if (Strings.areEqual(messageType, TYPE_PING)) {
            lastPing = LocalDateTime.now();
            interconnect.dispatch(getName(),
                                  Json.createObject()
                                      .put(MESSAGE_TYPE, TYPE_PONG)
                                      .put(MESSAGE_NAME, CallContext.getNodeName())
                                      .put(MESSAGE_ADDRESS, getLocalAddress()));
        } else if (Strings.areEqual(messageType, TYPE_PONG)) {
            String address = event.path(MESSAGE_ADDRESS).asText(null);
            if (!Strings.areEqual(address, getLocalAddress()) && Strings.isFilled(address)) {
                String nodeName = event.path(MESSAGE_NAME).asText(null);
                if (!Strings.areEqual(members.put(nodeName, address), address)) {
                    Cluster.LOG.INFO("Discovered a new node: %s - %s", nodeName, address);
                }
            }
        } else if (Strings.areEqual(messageType, TYPE_KILL)) {
            members.remove(event.path(MESSAGE_NAME).asText(null));
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
        } catch (UnknownHostException _) {
            return "";
        }
    }

    /**
     * Removes a node as known cluster member.
     * <p>
     * Note that if the node is still alive, it will be re-discovered. Therefore, this should only be used to
     * remove nodes which are permanently shut down.
     *
     * @param name the name of the node to remove as member
     */
    public void killNode(String name) {
        interconnect.dispatch(getName(), Json.createObject().put(MESSAGE_TYPE, TYPE_KILL).put(MESSAGE_NAME, name));
    }

    /**
     * Invokes the URI on each cluster member and returns the received JSON.
     * <p>
     * Note that this will not include the local node.
     *
     * @param uri the uri to invoke
     * @return the JSON per node as stream
     */
    public Stream<ObjectNode> callEachNode(String uri) {
        return members.entrySet().stream().map(e -> callNode(e.getKey(), e.getValue(), uri));
    }

    private ObjectNode callNode(String nodeName, String endpoint, String uri) {
        try {
            JSONCall call = JSONCall.to(new URI(endpoint + uri));

            // Set short-lived timeouts as we do not want to block a cluster wide query if one node is down...
            call.getOutcall().modifyClient(CLIENT_SELECTOR_CLUSTER).connectTimeout(SHORT_CLUSTER_HTTP_TIMEOUT);
            call.getOutcall().setReadTimeout(SHORT_CLUSTER_HTTP_TIMEOUT);

            ObjectNode result = call.getInput();
            result.put(RESPONSE_NODE_NAME, nodeName);
            if (!result.has(RESPONSE_ERROR)) {
                result.put(RESPONSE_ERROR, false);
            }

            return result;
        } catch (Exception exception) {
            return Json.createObject()
                       .put(RESPONSE_NODE_NAME, nodeName)
                       .put(RESPONSE_ERROR, true)
                       .put(RESPONSE_ERROR_MESSAGE, exception.getMessage());
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

    private NodeInfo parseNodeState(ObjectNode response) {
        NodeInfo result = new NodeInfo();
        Json.tryValueString(response, RESPONSE_NODE_NAME).ifPresent(result::setName);
        if (response.path(RESPONSE_ERROR).asBoolean()) {
            result.setNodeState(MetricState.RED);
            return result;
        }

        result.setNodeState(MetricState.valueOf(Json.tryValueString(response, ClusterController.RESPONSE_NODE_STATE)
                                                    .orElse(null)));
        Json.tryValueString(response, ClusterController.RESPONSE_UPTIME).ifPresent(result::setUptime);

        ArrayNode nodeMetrics = Json.getArray(response, ClusterController.RESPONSE_METRICS);
        for (int i = 0; i < nodeMetrics.size(); i++) {
            try {
                ObjectNode jsonMetric = (ObjectNode) nodeMetrics.get(i);
                Metric metric =
                        new Metric(Json.tryValueString(jsonMetric, ClusterController.RESPONSE_CODE).orElse(null),
                                   Json.tryValueString(jsonMetric, ClusterController.RESPONSE_LABEL).orElse(null),
                                   Json.tryGet(jsonMetric, ClusterController.RESPONSE_VALUE)
                                       .map(JsonNode::asDouble)
                                       .orElse(null),
                                   MetricState.valueOf(Json.tryValueString(jsonMetric, ClusterController.RESPONSE_STATE)
                                                           .orElse(null)),
                                   Json.tryValueString(jsonMetric, ClusterController.RESPONSE_UNIT).orElse(null));
                result.getMetrics().add(metric);
            } catch (Exception exception) {
                Exceptions.handle(Cluster.LOG, exception);
            }
        }

        return result;
    }
}
