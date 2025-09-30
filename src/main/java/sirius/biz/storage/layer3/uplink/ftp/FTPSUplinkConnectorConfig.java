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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;

/**
 * Keeps the configuration used to build an FTPS connector using the
 * {@link sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool}.
 */
class FTPSUplinkConnectorConfig extends FTPUplinkConnectorConfig {

    /**
     * Specifies which SSL protocol to use.
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/24/docs/specs/security/standard-names.html#sslcontext-algorithms">list of possible values</a>
     */
    public static final String CONFIG_SSL_PROTOCOL = "sslProtocol";

    private final String sslProtocol;

    protected FTPSUplinkConnectorConfig(String id, Function<String, Value> config) {
        super(id, config);
        this.sslProtocol = config.apply(CONFIG_SSL_PROTOCOL).asString();
    }

    @Override
    protected FTPClient create() {
        try {
            FTPSClient client = new FTPSClient();

            if (Strings.isFilled(sslProtocol)) {
                client.setEnabledProtocols(new String[]{sslProtocol});
            }

            client.setConnectTimeout(connectTimeoutMillis);
            client.setDataTimeout(Duration.ofMillis(readTimeoutMillis));
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
