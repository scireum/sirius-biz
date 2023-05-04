/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.di.std.Named;

/**
 * Represents a handler which takes care of a pub/sub message distributed by the {@link Interconnect}.
 */
public interface InterconnectHandler extends Named {

    /**
     * Handles a JSON object which is distributed via the <tt>Interconnect</tt> and contains a <tt>_handler</tt>
     * property matching our name.
     *
     * @param event the event to process
     */
    void handleEvent(ObjectNode event);
}
