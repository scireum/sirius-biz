/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.storage.vfs.ftp;

import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;
import sirius.biz.jdbc.storage.vfs.VirtualFileSystem;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Takes care of user management and session monitoring.
 */
class BridgeFtplet implements Ftplet {

    private final Set<FtpSession> openSessions = Collections.synchronizedSet(new HashSet<FtpSession>());

    @ConfigValue("storage.ftp.maxConnectionsPerIp")
    private static int maxConnectionsPerIp;

    @Override
    public void init(FtpletContext ftpletContext) throws FtpException {
        // not needed
    }

    @Override
    public void destroy() {
        // not needed
    }

    @Override
    public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException {
        if (VirtualFileSystem.LOG.isFINE()) {
            VirtualFileSystem.LOG.FINE("BEFORE FTP: " + request.getCommand());
        }

        UserContext.get().setCurrentScope(ScopeInfo.DEFAULT_SCOPE);

        if (session.getUser() instanceof BridgeUser) {
            if (VirtualFileSystem.LOG.isFINE()) {
                VirtualFileSystem.LOG.FINE("Setting user: " + session.getUser().getName());
            }
            UserContext.get().setCurrentUser(((BridgeUser) session.getUser()).getAuthUser());
        } else {
            if (VirtualFileSystem.LOG.isFINE()) {
                VirtualFileSystem.LOG.FINE("Setting anonymous user...");
            }
            UserContext.get().setCurrentUser(UserInfo.NOBODY);
        }

        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply)
            throws FtpException, IOException {
        if (VirtualFileSystem.LOG.isFINE()) {
            VirtualFileSystem.LOG.FINE("AFTER FTP: " + request.getCommand());
        }

        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onConnect(FtpSession session) throws FtpException, IOException {
        long connectionsForThisIP = openSessions.stream()
                                                .filter(openSession -> Strings.areEqual(openSession.getSessionId(),
                                                                                        session.getSessionId()))
                                                .count();
        if (connectionsForThisIP >= maxConnectionsPerIp) {
            throw new FtpException("No more than " + maxConnectionsPerIp + " connections per IP accepted!");
        }
        openSessions.add(session);
        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
        openSessions.removeIf(openSession -> Strings.areEqual(openSession.getSessionId(), session.getSessionId()));
        return FtpletResult.DEFAULT;
    }
}
