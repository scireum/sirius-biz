/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.sftp.server.SftpErrorStatusDataHandler;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemConfigurator;
import sirius.biz.storage.layer3.downlink.ssh.BridgeSession;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.web.security.ScopeDetector;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Wraps the SFTP subsystem to execute as the correct {@link UserInfo user}.
 */
class BridgeSftpSubsystem extends SftpSubsystem {

    @Part
    @Nullable
    private static ScopeDetector detector;

    protected BridgeSftpSubsystem(ChannelSession channel, SftpSubsystemConfigurator configurator) {
        super(channel, configurator);
    }

    @Override
    public void run() {
        try {
            UserInfo user = ((BridgeSession) getServerSession()).getUser();
            String scopeId = ((BridgeSession) getServerSession()).getScopeId();

            UserContext.get().setCurrentScope(ScopeInfo.DEFAULT_SCOPE);
            if (Strings.isFilled(scopeId) && detector != null) {
                detector.findScopeById(scopeId).ifPresent(UserContext.get()::setCurrentScope);
            }

            UserContext.get().runAs(user, super::run);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 3/SFTP: An error occurred: %s (%s)")
                      .handle();
        }
    }

    @Override
    protected void sendStatus(Buffer buffer, int id, Throwable e, int cmd, Object... args) throws IOException {
        if (e instanceof HandledException handledException) {
            SftpErrorStatusDataHandler handler = getErrorStatusDataHandler();
            int subStatus = handler.resolveSubStatus(this, id, e, cmd, args);
            String message = Strings.apply("%s: %s",
                                           handler.resolveErrorMessage(this, id, e, subStatus, cmd, args),
                                           handledException.getLocalizedMessage());
            String lang = handler.resolveErrorLanguage(this, id, e, subStatus, cmd, args);
            sendStatus(buffer, id, subStatus, message, lang);
        } else {
            super.sendStatus(buffer, id, e, cmd, args);
        }
    }
}
