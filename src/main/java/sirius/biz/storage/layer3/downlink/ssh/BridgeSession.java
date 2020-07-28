/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.session.ServerSessionImpl;
import sirius.web.security.UserInfo;

/**
 * Extends the server session by dragging our user along.
 */
public class BridgeSession extends ServerSessionImpl {

    private UserInfo user = UserInfo.NOBODY;
    private String scopeId;

    protected BridgeSession(ServerFactoryManager factoryManager, IoSession ioSession) throws Exception {
        super(factoryManager, ioSession);
    }

    /**
     * The user which has been attached to the session.
     *
     * @return the user attached to this session
     */
    public UserInfo getUser() {
        return user;
    }

    /**
     * Attaches the given user to the session.
     *
     * @param user the user to attach
     */
    public void attachUser(UserInfo user) {
        this.user = user;
    }

    public void attachScope(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getScopeId() {
        return scopeId;
    }
}
