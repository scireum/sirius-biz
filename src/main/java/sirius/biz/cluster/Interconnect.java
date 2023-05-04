/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.db.redis.Redis;
import sirius.db.redis.Subscriber;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

/**
 * Provides a pusblish / subscribe model to broadcast messages across all nodes of the cluster.
 */
@Register(classes = {Interconnect.class, Subscriber.class})
public class Interconnect implements Subscriber {

    /**
     * Contains the logger used for all messages concerning the interconnect.
     */
    @SuppressWarnings("squid:S1192")
    @Explain("Constants are semantically different")
    public static final Log LOG = Log.get("interconnect");

    /**
     * Contains the name of the pub-sub topic which is used to distribute messages to downlinks (Websockets).
     */
    public static final String TOPIC_INTERCONNECT = "interconnect";

    /**
     * Contains the name of the key which is used to extract the handler class for handling an
     * incoming mesage.
     */
    public static final String HANDLER = "_handler";

    @Part
    private GlobalContext ctx;

    @Part
    private Redis redis;

    /**
     * Dispatches a JSON message across all nodes via <tt>Redis Pub/Sub</tt>.
     * <p>
     * The appropriate {@link InterconnectHandler} with the given name will be invoked on each node, including this one.
     *
     * @param handler the handler to send the event to
     * @param event   the JSON data to publish
     */
    public void dispatch(String handler, ObjectNode event) {
        event.put(HANDLER, handler);

        if (!redis.isConfigured()) {
            dispatchLocally(event);
            return;
        }

        String msg = Json.write(event);
        if (LOG.isFINE()) {
            LOG.FINE("Sending message: " + msg);
        }
        try {
            redis.publish(TOPIC_INTERCONNECT, msg);
        } catch (Exception e) {
            Exceptions.handle(LOG, e);
        }
    }

    @Override
    public String getTopic() {
        return TOPIC_INTERCONNECT;
    }

    @Override
    public void onMessage(String message) {
        try {
            if (LOG.isFINE()) {
                LOG.FINE("Received message: " + message);
            }
            ObjectNode msgAsJSON = Json.parseObject(message);
            dispatchLocally(msgAsJSON);
        } catch (Exception e) {
            Exceptions.handle(LOG, e);
        }
    }

    private void dispatchLocally(ObjectNode event) {
        String type = event.get(HANDLER).asText();
        if (Strings.isEmpty(type)) {
            throw new IllegalArgumentException("handler must not be empty!");
        }

        ctx.findPart(type, InterconnectHandler.class).handleEvent(event);
    }
}
