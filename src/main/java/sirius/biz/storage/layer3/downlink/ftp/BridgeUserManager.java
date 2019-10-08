/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ftp;

import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import sirius.biz.storage.util.StorageUtils;
import sirius.web.security.MaintenanceInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

/**
 * Provides a bridge between {@link UserContext} and the FTP server.
 */
class BridgeUserManager implements UserManager {

    @Override
    public User getUserByName(String s) throws FtpException {
        return null;
    }

    @Override
    public String[] getAllUserNames() throws FtpException {
        throw new UnsupportedOperationException("getAllUserNames");
    }

    @Override
    public void delete(String s) throws FtpException {
        throw new UnsupportedOperationException("delete");
    }

    @Override
    public void save(User user) throws FtpException {
        throw new UnsupportedOperationException("save");
    }

    @Override
    public boolean doesExist(String s) throws FtpException {
        throw new UnsupportedOperationException("doesExist");
    }

    @Override
    public User authenticate(Authentication authentication) throws AuthenticationFailedException {
        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/FTP: Incoming auth-request: " + authentication);
        }
        if (!(authentication instanceof UsernamePasswordAuthentication)) {
            StorageUtils.LOG.FINE("Layer 3/FTP: Not a UsernamePasswordAuthentication...aborting");
            throw new AuthenticationFailedException("Please use username/password to authenticate.");
        }
        boolean locked =
                UserContext.getCurrentScope().tryAs(MaintenanceInfo.class).map(MaintenanceInfo::isLocked).orElse(false);
        if (locked) {
            throw new AuthenticationFailedException("The system is currently locked for maintenance reasons.");
        }

        UsernamePasswordAuthentication auth = (UsernamePasswordAuthentication) authentication;
        StorageUtils.LOG.FINE("Layer 3/FTP: Trying to authenticate user: " + auth.getUsername());

        try {
            String effectiveUserName = auth.getUsername().replace("_AT_", "@");
            UserInfo authUser = UserContext.get()
                                           .getUserManager()
                                           .findUserByCredentials(null, effectiveUserName, auth.getPassword());
            if (authUser == null) {
                StorageUtils.LOG.FINE("Layer 3/FTP: Invalid credentails...");
                throw new AuthenticationFailedException("Invalid credentials.");
            }
            StorageUtils.LOG.FINE("Layer 3/FTP: User is authorized...");
            return new BridgeUser(authUser);
        } catch (Exception e) {
            throw new AuthenticationFailedException(e.getMessage());
        }
    }

    @Override
    public String getAdminName() throws FtpException {
        throw new UnsupportedOperationException("getAdminName");
    }

    @Override
    public boolean isAdmin(String s) throws FtpException {
        throw new UnsupportedOperationException("isAdmin");
    }
}
