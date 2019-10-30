/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionImpl;
import org.apache.sshd.server.session.SessionFactory;
import sirius.biz.storage.layer3.downlink.ssh.scp.BridgeScpCommandFactory;
import sirius.biz.storage.layer3.downlink.ssh.sftp.BridgeSftpSubsystemFactory;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.Killable;
import sirius.kernel.Startable;
import sirius.kernel.Stoppable;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.web.security.MaintenanceInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Collections;

/**
 * Provides a built-in SSH server which provides access to the {@link sirius.biz.storage.layer3.VirtualFile} via
 * <b>SCP</b> and <b>SFTP</b>.
 */
@Register
public class SSHServer implements Startable, Stoppable, Killable {

    @ConfigValue("storage.layer3.downlink.ssh.port")
    private int port;

    @ConfigValue("storage.layer3.downlink.ssh.hostKeyFile")
    private String hostKeyFile;

    private SshServer server;

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY + 200;
    }

    @Override
    public void started() {
        if (port <= 0) {
            return;
        }

        try {
            disableLogging();

            server = SshServer.setUpDefaultServer();
            server.setPort(port);
            server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File(hostKeyFile).toPath()));

            installSCPCommandFactory();
            installSessionFactory();
            installPasswordAuthenticator();
            installFileSystemFactory();
            installSFTPSubsystem();

            server.start();
            StorageUtils.LOG.WARN("Layer 3/SSH: Successfully started the SSH server on port %s", port);
        } catch (IOException e) {
            StorageUtils.LOG.WARN("Layer 3/SSH: Failed to start the SSH server: %s", e.getMessage());
        }
    }

    private void disableLogging() {
        // Some parts of the scp/sftp sub systems are too chatty...
        Logger.getLogger("sirius.biz.storage.layer3.downlink.ssh.BridgeSession").setLevel(Level.WARN);
    }

    protected void installSCPCommandFactory() {
        server.setCommandFactory(new BridgeScpCommandFactory());
    }

    protected void installSessionFactory() {
        server.setSessionFactory(new SessionFactory(server) {
            @Override
            protected ServerSessionImpl doCreateSession(IoSession ioSession) throws Exception {
                return new BridgeSession(getFactoryManager(), ioSession);
            }
        });
    }

    protected void installPasswordAuthenticator() {
        server.setPasswordAuthenticator(this::authenticate);
    }

    private boolean authenticate(String username, String password, ServerSession session) {
        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/SSH: Incoming auth-request: " + session);
        }

        boolean locked =
                UserContext.getCurrentScope().tryAs(MaintenanceInfo.class).map(MaintenanceInfo::isLocked).orElse(false);
        if (locked) {
            return false;
        }

        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/FTP: Trying to authenticate user: " + username);
        }

        UserInfo authUser = UserContext.get().getUserManager().findUserByCredentials(null, username, password);
        ((BridgeSession) session).setUser(authUser);

        return authUser != null;
    }

    protected void installFileSystemFactory() {
        server.setFileSystemFactory(new FileSystemFactory() {
            @Override
            public FileSystem createFileSystem(Session session) throws IOException {
                return new BridgeFileSystem();
            }
        });
    }

    protected void installSFTPSubsystem() {
        server.setSubsystemFactories(Collections.singletonList(new BridgeSftpSubsystemFactory()));
    }

    @Override
    public void stopped() {
        if (server != null && server.isStarted()) {
            try {
                server.stop();
            } catch (IOException e) {
                StorageUtils.LOG.WARN("Layer 3/SSH: Failed to stop the SSH server: %s", e.getMessage());
            }
        }
    }

    @Override
    public void awaitTermination() {
        if (server != null && server.isStarted()) {
            try {
                server.stop(true);
            } catch (IOException e) {
                StorageUtils.LOG.WARN("Layer 3/SSH: Failed to terminate the SSH server: %s", e.getMessage());
            }
        }
    }
}
