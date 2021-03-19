/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import sirius.biz.storage.layer3.uplink.util.UplinkConnectorConfig;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the configuration used to build a FTP connector using the
 * {@link sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool}.
 */
class FTPUplinkConnectorConfig extends UplinkConnectorConfig<FTPClient> {

    private static final int DEFAULT_FTP_PORT = 21;

    private final String encoding;

    protected FTPUplinkConnectorConfig(Extension config) {
        super(config);

        this.encoding = config.get("encoding").asString(StandardCharsets.UTF_8.name());
    }

    @Override
    protected int getDefaultPort() {
        return DEFAULT_FTP_PORT;
    }

    @Override
    protected FTPClient create() {
        try {
            FTPClient client = new FTPClient();
            client.setConnectTimeout(connectTimeoutMillis);
            client.setDataTimeout(readTimeoutMillis);
            client.setDefaultTimeout(readTimeoutMillis);

            client.setControlEncoding(encoding);

            client.connect(host, port);
            client.login(user, password);
            client.setFileType(FTP.BINARY_FILE_TYPE);
            client.enterLocalPassiveMode();

            return client;
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: An error occurred while connecting the uplink %s: %s (%s)",
                                    this)
                            .handle();
        }
    }

    @Override
    protected boolean validate(FTPClient connector) {
        return connector.isAvailable();
    }

    @Override
    protected void safeClose(FTPClient connector) {
        try {
            connector.disconnect();
        } catch (IOException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 3/FTP: An error occurred while disconnecting the uplink %s: %s (%s)",
                              this)
                      .handle();
        }
    }
}
