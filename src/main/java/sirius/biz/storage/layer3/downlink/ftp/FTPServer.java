/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ftp;

import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.tenants.AdditionalRolesProvider;
import sirius.biz.tenants.UserAccount;
import sirius.kernel.Startable;
import sirius.kernel.Stoppable;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Provides a bridge between the {@link sirius.biz.storage.layer3.VirtualFileSystem} and the Apache FTP server.
 */
@Register(classes = {Startable.class, Stoppable.class, AdditionalRolesProvider.class, FTPServer.class})
public class FTPServer implements Startable, Stoppable, AdditionalRolesProvider {

    /**
     * Defines a role defining if the FTP server is enabled in this system.
     */
    public static final String ROLE_FTP_SERVER_ENABLED = "ftp-server-enabled";

    private FtpServer server;

    @ConfigValue("storage.layer3.downlink.ftp.port")
    private int ftpPort;

    @ConfigValue("storage.layer3.downlink.ftp.passivePorts")
    private String passivePorts;

    @ConfigValue("storage.layer3.downlink.ftp.bindAddress")
    private String bindAddress;

    @ConfigValue("storage.layer3.downlink.ftp.passiveExternalAddress")
    private String passiveExternalAddress;

    @ConfigValue("storage.layer3.downlink.ftp.idleTimeout")
    private Duration idleTimeout;

    @ConfigValue("storage.layer3.downlink.ftp.keystore")
    private String keystore;

    @ConfigValue("storage.layer3.downlink.ftp.keystorePassword")
    private String keystorePassword;

    @ConfigValue("storage.layer3.downlink.ftp.keyAlias")
    private String keyAlias;

    @ConfigValue("storage.layer3.downlink.ftp.forceSSL")
    private boolean forceSSL;

    @ConfigValue("storage.layer3.downlink.ftp.tlsProtocol")
    private String tlsProtocol;

    @ConfigValue("storage.layer3.downlink.ftp.externalHost")
    private String externalHost;

    @ConfigValue("storage.layer3.downlink.ftp.externalPort")
    private int externalPort;

    @ConfigValue("product.baseUrl")
    private String productBaseUrl;

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY + 100;
    }

    @Override
    public void started() {
        if (ftpPort <= 0) {
            return;
        }

        disableLogging();
        createFTPServer();
        startFTPServer();
    }

    @Override
    public void stopped() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception exception) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .error(exception)
                          .withSystemErrorMessage("Layer3/FTP: Failed to stop FTP server on port %s (%s): %s (%s)",
                                                  ftpPort,
                                                  bindAddress)
                          .handle();
            }
        }
    }

    private void startFTPServer() {
        try {
            server.start();
            StorageUtils.LOG.INFO("Layer3/FTP: Started FTP server on port %s (%s)", ftpPort, bindAddress);
        } catch (FtpException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Layer3/FTP: Failed to start FTP server on port %s (%s): %s (%s)",
                                              ftpPort,
                                              bindAddress)
                      .handle();
        }
    }

    private void createFTPServer() {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory factory = new ListenerFactory();
        setupNetwork(factory);
        setupSSL(factory);
        setupFtplets(serverFactory);

        serverFactory.setFileSystem(ignored -> new BridgeFileSystemView());
        serverFactory.setUserManager(new BridgeUserManager());
        serverFactory.setConnectionConfig(new ConfigBasedConnectionConfig());

        serverFactory.addListener("default", factory.createListener());

        server = serverFactory.createServer();
    }

    private void setupNetwork(ListenerFactory factory) {
        factory.setPort(ftpPort);
        factory.setIdleTimeout((int) idleTimeout.getSeconds());

        if (Strings.isFilled(bindAddress)) {
            factory.setServerAddress(bindAddress);
        }

        DataConnectionConfigurationFactory dataConnectionConfigurationFactory =
                new DataConnectionConfigurationFactory();
        dataConnectionConfigurationFactory.setIdleTime((int) idleTimeout.getSeconds());

        if (Strings.isFilled(passivePorts)) {
            dataConnectionConfigurationFactory.setPassivePorts(passivePorts);
        }

        if (Strings.isFilled(passiveExternalAddress)) {
            dataConnectionConfigurationFactory.setPassiveExternalAddress(passiveExternalAddress);
        }

        factory.setDataConnectionConfiguration(dataConnectionConfigurationFactory.createDataConnectionConfiguration());
    }

    private void setupFtplets(FtpServerFactory serverFactory) {
        Map<String, Ftplet> ftplets = new TreeMap<>();
        ftplets.put("bridge", new BridgeFtplet());
        serverFactory.setFtplets(ftplets);
    }

    private void setupSSL(ListenerFactory factory) {
        if (Strings.isEmpty(keystore)) {
            return;
        }

        StorageUtils.LOG.INFO("Layer3/FTP: Setting up SFTP support using key %s of store %s", keyAlias, keystore);
        SslConfigurationFactory ssl = new SslConfigurationFactory();
        ssl.setKeystoreFile(new File(keystore));
        ssl.setKeystorePassword(keystorePassword);
        ssl.setKeyAlias(keyAlias);
        ssl.setSslProtocol(new String[]{tlsProtocol});
        factory.setSslConfiguration(ssl.createSslConfiguration());
        factory.setImplicitSsl(forceSSL);
    }

    private void disableLogging() {
        // The Apache FTP Server is wayyy too chatty.....
        Log.setLevel("org.apache.ftpserver", Level.SEVERE);
    }

    /**
     * Returns the host name as exposed to the outside world.
     *
     * @return the host name
     */
    public String computeExternalHost() {
        if (Strings.isFilled(externalHost)) {
            return externalHost;
        }

        if (Strings.isFilled(passiveExternalAddress)) {
            return passiveExternalAddress;
        }

        return productBaseUrl;
    }

    /**
     * Returns the port as exposed to the outside world.
     *
     * @return the port number
     */
    public int computeExternalPort() {
        if (externalPort > 0) {
            return externalPort;
        }

        return ftpPort;
    }

    /**
     * Returns if FTP over TLS is enabled in this server.
     *
     * @return <tt>true</tt> if SSH is enabled, <tt>false</tt> otherwise
     */
    public boolean enabledTLS() {
        return Strings.isFilled(keystore);
    }

    @Override
    public void addAdditionalRoles(UserAccount<?, ?> user, Consumer<String> roleConsumer) {
        if (ftpPort > 0) {
            roleConsumer.accept(ROLE_FTP_SERVER_ENABLED);
        }
    }
}
