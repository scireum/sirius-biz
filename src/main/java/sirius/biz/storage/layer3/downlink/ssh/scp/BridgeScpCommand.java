/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.common.scp.ScpFileOpener;
import org.apache.sshd.common.scp.ScpTransferEventListener;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.server.scp.ScpCommand;
import sirius.biz.storage.layer3.downlink.ssh.BridgeSession;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

class BridgeScpCommand extends ScpCommand {

    protected BridgeScpCommand(String command,
                               CloseableExecutorService executorService,
                               int sendSize,
                               int receiveSize,
                               ScpFileOpener fileOpener,
                               ScpTransferEventListener eventListener) {
        super(command, executorService, sendSize, receiveSize, fileOpener, eventListener);
    }

    @Override
    public void run() {
        UserInfo user = ((BridgeSession) getServerSession()).getUser();
        UserContext.get().runAs(user, super::run);
    }
}
