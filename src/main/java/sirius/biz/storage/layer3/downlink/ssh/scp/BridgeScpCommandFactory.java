/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.scp.common.ScpTransferEventListener;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

/**
 * Provides the effective SCP command to use.
 *
 * @see BridgeScpCommand
 */
public class BridgeScpCommandFactory extends ScpCommandFactory implements ScpTransferEventListener {

    /**
     * Creates a new factor to be supplied to a {@link org.apache.sshd.server.SshServer}.
     */
    public BridgeScpCommandFactory() {
        setScpFileOpener(new BridgeScpFileOpener());
    }

    @Override
    protected Command executeSupportedCommand(ChannelSession channel, String command) {
        return new BridgeScpCommand(channel,
                                    command,
                                    resolveExecutorService(),
                                    getSendBufferSize(),
                                    getReceiveBufferSize(),
                                    getScpFileOpener(),
                                    this);
    }
}
