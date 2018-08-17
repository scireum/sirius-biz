/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.vfs.ftp;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import sirius.biz.vfs.VirtualFileSystem;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides a bridge between the {@link VirtualFileSystem} and the Apache FTP server.
 */
@Register(classes = FTPBridge.class)
public class FTPBridge {

    private FtpServer ftpServer;

    @ConfigValue("vfs.ftp.port")
    private int ftpPort;

    @ConfigValue("vfs.ftp.bindAddress")
    private String bindAddress;

    @ConfigValue("vfs.ftp.idleTimeout")
    private Duration idleTimeout;

    @ConfigValue("vfs.ftp.keystore")
    private String keystore;

    @ConfigValue("vfs.ftp.keystorePassword")
    private String keystorePassword;

    @ConfigValue("vfs.ftp.keyAlias")
    private String keyAlias;

    @ConfigValue("vfs.ftp.forceSSL")
    private boolean forceSSL;

    /**
     * Initializes and starts the server.
     */
    public void createAndStartServer() {
        if (ftpPort <= 0) {
            return;
        }

        disableLogging();
        createFTPServer();
        startFTPServer();
    }

    /**
     * Stops the server.
     */
    public void stop() {
        if (ftpServer != null) {
            try {
                ftpServer.stop();
            } catch (Exception e) {
                Exceptions.handle()
                          .to(VirtualFileSystem.LOG)
                          .error(e)
                          .withSystemErrorMessage("Failed to stop FTP server on port %s (%s): %s (%s)",
                                                  ftpPort,
                                                  bindAddress)
                          .handle();
            }
        }
    }

    private void startFTPServer() {
        try {
            ftpServer.start();
            VirtualFileSystem.LOG.INFO("Started FTP server on port %s (%s)", ftpPort, bindAddress);
        } catch (FtpException e) {
            Exceptions.handle()
                      .to(VirtualFileSystem.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to start FTP server on port %s (%s): %s (%s)",
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

        ftpServer = serverFactory.createServer();
    }

    private void setupNetwork(ListenerFactory factory) {
        factory.setPort(ftpPort);
        factory.setIdleTimeout((int) idleTimeout.getSeconds());
        if (Strings.isFilled(bindAddress)) {
            factory.setServerAddress(bindAddress);
        }
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

        SslConfigurationFactory ssl = new SslConfigurationFactory();
        ssl.setKeystoreFile(new File(keystore));
        ssl.setKeystorePassword(keystorePassword);
        ssl.setKeyAlias(keyAlias);
        factory.setSslConfiguration(ssl.createSslConfiguration());
        factory.setImplicitSsl(forceSSL);
    }

    private void disableLogging() {
        // The Apache FTP Server is wayyy too chatty.....
        Logger.getLogger("org.apache.ftpserver").setLevel(Level.ERROR);
    }
}
