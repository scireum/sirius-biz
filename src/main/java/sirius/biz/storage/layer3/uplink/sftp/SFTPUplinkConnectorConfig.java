/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.sftp;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory;
import sirius.biz.storage.layer3.uplink.util.UplinkConnectorConfig;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * Keeps the configuration used to build a SFTP connector using the
 * {@link sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool}.
 */
class SFTPUplinkConnectorConfig extends UplinkConnectorConfig<SftpClient> {

    private static final int DEFAULT_SFTP_PORT = 22;
    private SshClient sshClient;

    protected SFTPUplinkConnectorConfig(Extension config) {
        super(config);
    }

    @Override
    protected int getDefaultPort() {
        return DEFAULT_SFTP_PORT;
    }

    private SshClient getClient() {
        if (sshClient == null) {
            sshClient = SshClient.setUpDefaultClient();
            sshClient.getProperties().putIfAbsent(CoreModuleProperties.IDLE_TIMEOUT.getName(), idleTimeoutMillis);
            sshClient.getProperties().putIfAbsent(CoreModuleProperties.NIO2_READ_TIMEOUT.getName(), readTimeoutMillis);
            sshClient.setHostConfigEntryResolver(new HostConfigEntryResolver() {
                @Override
                public HostConfigEntry resolveEffectiveHost(String host,
                                                            int port,
                                                            SocketAddress localAddress,
                                                            String username,
                                                            String proxyJump,
                                                            AttributeRepository context) throws IOException {
                    return new HostConfigEntry(host, host, port, username);
                }
            });
            sshClient.start();
        }

        return sshClient;
    }

    @Override
    protected SftpClient create() {
        try {
            return createSession();
        } catch (IOException e) {
            throw Exceptions.handle()
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/SFTP: An error occurred while connecting the uplink %s: %s (%s)",
                                    this)
                            .handle();
        }
    }

    private SftpClient createSession() throws IOException {
        ClientSession session = getClient().connect(user, host, port).verify(connectTimeoutMillis).getSession();
        try {
            session.addPasswordIdentity(password);
            session.auth().verify(connectTimeoutMillis);
            return DefaultSftpClientFactory.INSTANCE.createSftpClient(session);
        } catch (Exception e) {
            safeAbortLaunch(session);

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/SFTP: An error occurred while connecting the uplink %s: %s (%s)",
                                    this)
                            .handle();
        }
    }

    private void safeAbortLaunch(ClientSession session) {
        try {
            session.close();
        } catch (Exception ex) {
            Exceptions.ignore(ex);
        }
    }

    @Override
    protected boolean validate(SftpClient connector) {
        return connector.isOpen();
    }

    @Override
    protected void safeClose(SftpClient connector) {
        ClientSession session = connector.getSession();
        try {
            connector.close();
        } catch (IOException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 3/SFTP: An error occurred while disconnecting the uplink %s: %s (%s)",
                              this)
                      .handle();
        }
        try {
            session.close();
        } catch (IOException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 3/SFTP: An error occurred while disconnecting the uplink %s: %s (%s)",
                              this)
                      .handle();
        }
    }
}
