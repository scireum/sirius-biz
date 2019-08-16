/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink;

import sirius.kernel.di.std.Named;
import sirius.kernel.settings.Extension;

/**
 * Creates the uplink with the given type.
 */
public interface ConfigBasedUplinkFactory extends Named {

    /**
     * Instantiates the uplink based on the given config.
     *
     * @param config the config section to use
     * @return the uplink based on the given config
     * @throws IllegalArgumentException in case of an invalid configuration
     */
    ConfigBasedUplink make(Extension config) throws IllegalArgumentException;
}
