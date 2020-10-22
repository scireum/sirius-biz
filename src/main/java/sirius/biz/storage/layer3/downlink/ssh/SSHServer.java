/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.server.ServerFactoryManager;
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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;
import sirius.web.security.MaintenanceInfo;
import sirius.web.security.ScopeDetector;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.time.Duration;
import java.util.Collections;
import java.util.logging.Level;

/**
 * Provides a built-in SSH server which provides access to the {@link sirius.biz.storage.layer3.VirtualFile} via
 * <b>SCP</b> and <b>SFTP</b>.
 */
@Register
public class SSHServer implements Startable, Stoppable, Killable {

    @ConfigValue("storage.layer3.downlink.ssh.port")
    private int port;

    @ConfigValue("storage.layer3.downlink.ssh.idleTimeout")
    private Duration idleTimeout;

    @ConfigValue("storage.layer3.downlink.ssh.readTimeout")
    private Duration readTimeout;

    @ConfigValue("storage.layer3.downlink.ssh.hostKeyFile")
    private String hostKeyFile;

    private SshServer server;

    @Part
    @Nullable
    private ScopeDetector detector;

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
            server.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, idleTimeout.toMillis());
            server.getProperties().put(ServerFactoryManager.NIO2_READ_TIMEOUT, readTimeout.toMillis());
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
        Log.setLevel("sirius.biz.storage.layer3.downlink.ssh.BridgeSession", Level.WARNING);
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

        if (username.contains("\\") && detector != null) {
            Tuple<String, String> scopeAndUsername = Strings.split(username, "\\");
            UserContext.get().setCurrentScope(detector.findScopeByName(scopeAndUsername.getFirst()));
            username = scopeAndUsername.getSecond();
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
        ((BridgeSession) session).attachUser(authUser);

        if (!ScopeInfo.DEFAULT_SCOPE.equals(UserContext.getCurrentScope())) {
            ((BridgeSession) session).attachScope(UserContext.getCurrentScope().getScopeId());
        }

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
