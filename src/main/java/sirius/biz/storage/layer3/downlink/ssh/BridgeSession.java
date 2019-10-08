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

public class BridgeSession extends ServerSessionImpl {

    private UserInfo user;

    protected BridgeSession(ServerFactoryManager factoryManager, IoSession ioSession) throws Exception {
        super(factoryManager, ioSession);
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }
}
