/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink;

import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Named;

import java.util.function.Function;

/**
 * Creates the uplink with the given type.
 */
public interface UplinkFactory extends Named {

    /**
     * Instantiates the uplink based on the given config.
     *
     * @param id     the unique identifier of the uplink. This will also most probably be the name of the directory created
     *               in the {@link sirius.biz.storage.layer3.VirtualFileSystem}
     * @param config the config section to use
     * @return the uplink based on the given config
     * @throws IllegalArgumentException in case of an invalid configuration
     */
    ConfigBasedUplink make(String id, Function<String, Value> config);
}
