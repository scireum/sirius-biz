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
import org.apache.commons.net.ftp.FTPSClient;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.util.function.Function;

/**
 * Keeps the configuration used to build an FTPS connector using the
 * {@link sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool}.
 */
class FTPSUplinkConnectorConfig extends FTPUplinkConnectorConfig {

    protected FTPSUplinkConnectorConfig(String id, Function<String, Value> config) {
        super(id, config);
    }

    @Override
    protected FTPClient create() {
        try {
            FTPSClient client = new FTPSClient();
            client.setConnectTimeout(connectTimeoutMillis);
            client.setDataTimeout(readTimeoutMillis);
            client.setDefaultTimeout(readTimeoutMillis);

            client.setControlEncoding(encoding);
            client.setDefaultPort(port);

            client.connect(host, port);
            client.login(user, password);
            client.setFileType(FTP.BINARY_FILE_TYPE);
            // Set protection buffer size
            client.execPBSZ(0);
            // Set data channel protection to private
            client.execPROT("P");
            client.enterLocalPassiveMode();

            return client;
        } catch (IOException exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: An error occurred while connecting the uplink %s: %s (%s)",
                                    this)
                            .handle();
        }
    }
}
