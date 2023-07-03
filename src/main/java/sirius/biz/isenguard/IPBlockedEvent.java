/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import sirius.biz.analytics.events.Event;
import sirius.db.mixing.Mapping;

/**
 * Recorded for each IP address blocked by {@link Isenguard#blockIP(String)}.
 */
public class IPBlockedEvent extends Event<IPBlockedEvent> {

    /**
     * Contains the IP address which has been blocked.
     */
    public static final Mapping IP = Mapping.named("ip");
    private String ip;

    public IPBlockedEvent withIp(String ip) {
        this.ip = ip;
        return this;
    }

    public String getIp() {
        return ip;
    }
}
