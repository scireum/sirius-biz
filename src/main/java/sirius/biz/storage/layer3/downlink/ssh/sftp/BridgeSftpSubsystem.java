/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.server.subsystem.sftp.SftpErrorStatusDataHandler;
import org.apache.sshd.server.subsystem.sftp.SftpFileSystemAccessor;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystem;
import org.apache.sshd.server.subsystem.sftp.UnsupportedAttributePolicy;
import sirius.biz.storage.layer3.downlink.ssh.BridgeSession;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

class BridgeSftpSubsystem extends SftpSubsystem {

    protected BridgeSftpSubsystem(CloseableExecutorService executorService,
                                  UnsupportedAttributePolicy policy,
                                  SftpFileSystemAccessor accessor,
                                  SftpErrorStatusDataHandler errorStatusDataHandler) {
        super(executorService, policy, accessor, errorStatusDataHandler);
    }

    @Override
    public void run() {
        UserInfo user = ((BridgeSession) getServerSession()).getUser();
        UserContext.get().runAs(user, super::run);
    }
}
