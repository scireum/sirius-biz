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
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
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
import sirius.kernel.di.std.Register;
import sirius.web.security.MaintenanceInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Collections;

@Register
public class SSHServer implements Startable, Stoppable, Killable {

    private SshServer server;

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public void started() {
        server = SshServer.setUpDefaultServer();
//        sshd.setShellFactory();
        server.setPort(2222);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath()));
        server.setCommandFactory(new BridgeScpCommandFactory());
        server.setSessionFactory(new SessionFactory(server) {
            @Override
            protected ServerSessionImpl doCreateSession(IoSession ioSession) throws Exception {
                return new BridgeSession(getFactoryManager(), ioSession);
            }
        });
        server.setPasswordAuthenticator(new PasswordAuthenticator() {
            @Override
            public boolean authenticate(String username, String password, ServerSession session) {
                if (StorageUtils.LOG.isFINE()) {
                    StorageUtils.LOG.FINE("Layer 3/SSH: Incoming auth-request: " + session);
                }

                boolean locked = UserContext.getCurrentScope()
                                            .tryAs(MaintenanceInfo.class)
                                            .map(MaintenanceInfo::isLocked)
                                            .orElse(false);
                if (locked) {
                    return false;
                }

                StorageUtils.LOG.FINE("Layer 3/FTP: Trying to authenticate user: " + username);

                UserInfo authUser = UserContext.get().getUserManager().findUserByCredentials(null, username, password);
                ((BridgeSession) session).setUser(authUser);

                return authUser != null;
            }
        });

        server.setFileSystemFactory(new FileSystemFactory() {
            @Override
            public FileSystem createFileSystem(Session session) throws IOException {
                return new BridgeFileSystem();
            }
        });
        server.setSubsystemFactories(Collections.singletonList(new BridgeSftpSubsystemFactory()));
        try {
            server.start();
        } catch (IOException e) {
            StorageUtils.LOG.WARN("Layer 3/SSH: Failed to start the SSH server: %s", e.getMessage());
        }
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
