/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ftp;

import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.AuthorizationRequest;
import org.apache.ftpserver.ftplet.User;
import sirius.web.security.UserInfo;

import java.util.Collections;
import java.util.List;

/**
 * Provides a simple bridge between {@link UserInfo} and the FTP server.
 */
class BridgeUser implements User {

    private UserInfo authUser;
    private String scopeId;

    BridgeUser(UserInfo authUser, String scopeId) {
        this.authUser = authUser;
        this.scopeId = scopeId;
    }

    @Override
    public String getName() {
        return authUser.getUserName();
    }

    @Override
    public AuthorizationRequest authorize(AuthorizationRequest request) {
        return request;
    }

    @Override
    public List<Authority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public List<Authority> getAuthorities(Class<? extends Authority> clazz) {
        return Collections.emptyList();
    }

    @Override
    public boolean getEnabled() {
        return true;
    }

    @Override
    public String getHomeDirectory() {
        return "/";
    }

    @Override
    public int getMaxIdleTime() {
        return 0;
    }

    @Override
    public String getPassword() {
        return null;
    }

    protected UserInfo getAuthUser() {
        return authUser;
    }

    protected String getScopeId() {
        return scopeId;
    }
}
