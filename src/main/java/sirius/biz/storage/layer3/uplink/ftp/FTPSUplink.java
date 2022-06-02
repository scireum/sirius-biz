/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.ftp;

import sirius.biz.storage.layer3.uplink.ConfigBasedUplink;
import sirius.biz.storage.layer3.uplink.UplinkFactory;
import sirius.biz.storage.layer3.uplink.util.RemotePath;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * Provides an uplink which connects to a remote FTPS server.
 */
public class FTPSUplink extends FTPUplink {

    /**
     * Creates a new uplink for config sections which use "ftp" as type.
     */
    @Register
    public static class Factory implements UplinkFactory {

        @Override
        public ConfigBasedUplink make(String id, Function<String, Value> config) {
            return new FTPSUplink(id,
                                  config,
                                  new FTPSUplinkConnectorConfig(id, config),
                                  new RemotePath(config.apply(CONFIG_BASE_PATH).asString("/")));
        }

        @Nonnull
        @Override
        public String getName() {
            return "ftps";
        }
    }

    protected FTPSUplink(String id,
                         Function<String, Value> config,
                         FTPUplinkConnectorConfig connectorConfig,
                         RemotePath basePath) {
        super(id, config, connectorConfig, basePath);
    }
}
