/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystem;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;

/**
 * Creates a new {@link BridgeSftpSubsystem} using the {@link BridgeFileSystemAccessor}.
 */
public class BridgeSftpSubsystemFactory extends SftpSubsystemFactory {

    /**
     * Creates a new instance if the {@link BridgeSftpSubsystem}.
     */
    public BridgeSftpSubsystemFactory() {
        setFileSystemAccessor(new BridgeFileSystemAccessor());
    }

    @Override
    public Command create() {
        SftpSubsystem subsystem = new BridgeSftpSubsystem(getExecutorService(),
                                                          getUnsupportedAttributePolicy(),
                                                          getFileSystemAccessor(),
                                                          getErrorStatusDataHandler());
        GenericUtils.forEach(getRegisteredListeners(), subsystem::addSftpEventListener);
        return subsystem;
    }
}
