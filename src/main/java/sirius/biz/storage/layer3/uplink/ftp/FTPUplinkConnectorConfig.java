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
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;

/**
 * Keeps the configuration used to build an FTP connector using the
 * {@link sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool}.
 */
class FTPUplinkConnectorConfig extends UplinkConnectorConfig<FTPClient> {

    private static final int DEFAULT_FTP_PORT = 21;

    /**
     * Specifies the encoding to use.
     */
    public static final String CONFIG_ENCODING = "encoding";

    protected final String encoding;

    protected FTPUplinkConnectorConfig(String id, Function<String, Value> config) {
        super(id, config);
        this.encoding = config.apply(CONFIG_ENCODING).asString(StandardCharsets.UTF_8.name());
    }

    @Override
    protected int getDefaultPort() {
        return DEFAULT_FTP_PORT;
    }

    @Override
    @SuppressWarnings("java:S5332")
    @Explain("A FTP uplink of course uses insecure FTP, which is not an issue with this code.")
    protected FTPClient create() {
        FTPClient client = new FTPClient();

        try {
            client.setConnectTimeout(connectTimeoutMillis);
            client.setDataTimeout(Duration.ofMillis(readTimeoutMillis));
            client.setDefaultTimeout(readTimeoutMillis);

            client.setControlEncoding(encoding);

            client.connect(host, port);
            login(client);
            client.setFileType(FTP.BINARY_FILE_TYPE);
            client.enterLocalPassiveMode();

            return client;
        } catch (Exception exception) {
            // If the client couldn't be fully initialized, it is never handed over to the connector pool and would
            // therefore keep its connection open until the JVM collects it...
            safeClose(client);

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: An error occurred while connecting the uplink %s: %s (%s)",
                                    this)
                            .handle();
        }
    }

    /**
     * Logs the given client in using the configured credentials.
     * <p>
     * Note that {@link FTPClient#login(String, String)} doesn't throw if the destination rejects the given
     * credentials but simply returns <tt>false</tt>. As an unauthenticated session cannot execute any command at
     * all, we abort right here instead of letting all subsequent commands fail with misleading errors.
     *
     * @param client the client to log in
     * @throws IOException                           if the login command itself fails
     * @throws sirius.kernel.health.HandledException if the destination rejected the configured credentials
     */
    protected void login(FTPClient client) throws IOException {
        if (client.login(user, password)) {
            return;
        }

        throw Exceptions.handle()
                        .to(StorageUtils.LOG)
                        .withSystemErrorMessage("Layer 3/FTP: The uplink %s rejected the given credentials: %s",
                                                this,
                                                client.getReplyString())
                        .handle();
    }

    @Override
    protected boolean validate(FTPClient connector) {
        return connector.isAvailable();
    }

    @Override
    protected void safeClose(FTPClient connector) {
        try {
            connector.disconnect();
        } catch (IOException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 3/FTP: An error occurred while disconnecting the uplink %s: %s (%s)",
                              this)
                      .handle();
        }
    }
}
