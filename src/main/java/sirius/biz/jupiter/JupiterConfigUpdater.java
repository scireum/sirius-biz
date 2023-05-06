/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.settings.Extension;

import java.util.Map;

/**
 * Provides a callback which can provide config settings which are transmitted to a Jupiter instance.
 */
@AutoRegister
public interface JupiterConfigUpdater {

    /**
     * Invoked to contribute to the config in <tt>config</tt> for the given instance.
     *
     * @param connector    the instance which is to be configured
     * @param systemConfig the extension / settings block for this instance in the system config
     * @param config       the config to contribute to
     */
    void emitConfig(JupiterConnector connector, Extension systemConfig, Map<String, Object> config);
}
