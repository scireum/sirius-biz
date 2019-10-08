/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.common.scp.ScpTransferEventListener;
import org.apache.sshd.common.util.EventListenerUtils;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.scp.ScpCommandFactory;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

public class BridgeScpCommandFactory extends ScpCommandFactory {

    private Collection<ScpTransferEventListener> listeners = new CopyOnWriteArraySet<>();
    private ScpTransferEventListener listenerProxy;

    public BridgeScpCommandFactory() {
        setScpFileOpener(new BridgeScpFileOpener());
        listenerProxy =
                EventListenerUtils.proxyWrapper(ScpTransferEventListener.class, getClass().getClassLoader(), listeners);
    }

    @Override
    protected Command executeSupportedCommand(String command) {
        return new BridgeScpCommand(command,
                                    getExecutorService(),
                                    getSendBufferSize(),
                                    getReceiveBufferSize(),
                                    getScpFileOpener(),
                                    listenerProxy);
    }

    @Override
    public BridgeScpCommandFactory clone() {
        BridgeScpCommandFactory other = getClass().cast(super.clone());
        other.listeners = new CopyOnWriteArraySet<>(this.listeners);
        other.listenerProxy = EventListenerUtils.proxyWrapper(ScpTransferEventListener.class,
                                                              getClass().getClassLoader(),
                                                              other.listeners);
        return other;
    }
}
