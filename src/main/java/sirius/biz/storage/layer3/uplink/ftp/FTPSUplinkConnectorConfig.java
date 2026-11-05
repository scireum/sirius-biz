/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.BCSSLSocket;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Provider;
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

    /**
     * Determines if data connections resume the TLS session of the control connection.
     * <p>
     * Many FTPS servers (e.g. FileZilla Server or vsftpd) reject data connections which do not resume the TLS session
     * of the control connection. If enabled, Bouncy Castle JSSE is used instead of the TLS implementation of the JDK,
     * as only the former permits to specify the session to resume.
     * <p>
     * Unless {@link #CONFIG_SSL_PROTOCOL} is set explicitly, this also enforces {@link #SESSION_REUSE_SSL_PROTOCOL},
     * as Bouncy Castle JSSE discards TLS 1.3 session tickets and therefore can only resume TLS 1.2 sessions.
     */
    public static final String CONFIG_SSL_SESSION_REUSE = "sslSessionReuse";

    /**
     * Contains the SSL protocol used by default if {@link #CONFIG_SSL_SESSION_REUSE} is enabled.
     */
    public static final String SESSION_REUSE_SSL_PROTOCOL = "TLSv1.2";

    /**
     * Contains the Bouncy Castle JSSE provider used if {@link #CONFIG_SSL_SESSION_REUSE} is enabled.
     * <p>
     * Note that the provider is intentionally not registered globally, so that all other TLS connections keep using
     * the implementation of the JDK. Therefore, the crypto provider has to be passed in explicitly, as the default
     * one fails to create its random generator unless Bouncy Castle is registered globally.
     */
    private static final Provider BOUNCY_CASTLE_JSSE_PROVIDER =
            new BouncyCastleJsseProvider(new BouncyCastleProvider());

    private static final String SSL_CONTEXT_PROTOCOL = "TLS";

    private final String sslProtocol;
    private final boolean sslSessionReuse;

    protected FTPSUplinkConnectorConfig(String id, Function<String, Value> config) {
        super(id, config);
        this.sslSessionReuse = config.apply(CONFIG_SSL_SESSION_REUSE).asBoolean();
        this.sslProtocol = Strings.firstFilled(config.apply(CONFIG_SSL_PROTOCOL).asString(),
                                               sslSessionReuse ? SESSION_REUSE_SSL_PROTOCOL : null);
    }

    @Override
    protected FTPClient create() {
        FTPSClient client = createClient();

        try {
            if (Strings.isFilled(sslProtocol)) {
                client.setEnabledProtocols(new String[]{sslProtocol});
            }

            client.setConnectTimeout(connectTimeoutMillis);
            client.setDataTimeout(Duration.ofMillis(readTimeoutMillis));
            client.setDefaultTimeout(readTimeoutMillis);

            client.setControlEncoding(encoding);
            client.setDefaultPort(port);

            client.connect(host, port);
            login(client);
            client.setFileType(FTP.BINARY_FILE_TYPE);
            // Set protection buffer size
            client.execPBSZ(0);
            // Set data channel protection to private
            client.execPROT("P");
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

    private FTPSClient createClient() {
        if (!sslSessionReuse) {
            return new FTPSClient();
        }

        try {
            SSLContext sslContext = SSLContext.getInstance(SSL_CONTEXT_PROTOCOL, BOUNCY_CASTLE_JSSE_PROVIDER);
            sslContext.init(null, null, null);
            return new SessionResumingFTPSClient(sslContext);
        } catch (GeneralSecurityException exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: Failed to create the TLS context for the uplink %s: %s (%s)",
                                    this)
                            .handle();
        }
    }

    /**
     * Resumes the TLS session of the control connection for each data connection.
     * <p>
     * The JDK only looks up sessions to resume by host and port, which never matches as the data connection uses
     * another port than the control connection. Therefore, the session is explicitly handed over using Bouncy Castle
     * JSSE, which requires the client to be created with an {@link SSLContext} provided by
     * {@link BouncyCastleJsseProvider}.
     */
    private static class SessionResumingFTPSClient extends FTPSClient {

        private SessionResumingFTPSClient(SSLContext sslContext) {
            super(sslContext);
        }

        @Override
        protected void _prepareDataSocket_(Socket socket) throws IOException {
            if (socket instanceof BCSSLSocket dataSocket && _socket_ instanceof BCSSLSocket controlSocket) {
                dataSocket.setBCSessionToResume(controlSocket.getBCSession());
            }
        }
    }
}
