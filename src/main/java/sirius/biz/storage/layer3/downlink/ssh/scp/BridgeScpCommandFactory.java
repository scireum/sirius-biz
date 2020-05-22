/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.common.scp.ScpTransferEventListener;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.scp.ScpCommandFactory;

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
    protected Command executeSupportedCommand(String command) {
        return new BridgeScpCommand(command,
                                    this.resolveExecutorService(),
                                    getSendBufferSize(),
                                    getReceiveBufferSize(),
                                    getScpFileOpener(),
                                    this);
    }
}
