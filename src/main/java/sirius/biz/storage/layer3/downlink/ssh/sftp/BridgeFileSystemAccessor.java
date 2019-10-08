/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.sftp.SftpEventListenerManager;
import org.apache.sshd.server.subsystem.sftp.SftpFileSystemAccessor;
import sirius.biz.storage.layer3.downlink.ssh.BridgeDirectoryStream;
import sirius.biz.storage.layer3.downlink.ssh.BridgeFileSystem;
import sirius.biz.storage.layer3.downlink.ssh.BridgePath;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

class BridgeFileSystemAccessor implements SftpFileSystemAccessor {

    @Override
    public SeekableByteChannel openFile(ServerSession session,
                                        SftpEventListenerManager subsystem,
                                        Path file,
                                        String handle,
                                        Set<? extends OpenOption> options,
                                        FileAttribute<?>... attrs) throws IOException {
        return new BridgeSeekableByteChannel(((BridgePath) file).getVirtualFile());
    }

    @Override
    public FileLock tryLock(ServerSession session,
                            SftpEventListenerManager subsystem,
                            Path file,
                            String handle,
                            Channel channel,
                            long position,
                            long size,
                            boolean shared) throws IOException {
        return null;
    }

    @Override
    public void syncFileData(ServerSession session,
                             SftpEventListenerManager subsystem,
                             Path file,
                             String handle,
                             Channel channel) throws IOException {
        System.out.println("");
    }

    @Override
    public DirectoryStream<Path> openDirectory(ServerSession session,
                                               SftpEventListenerManager subsystem,
                                               Path dir,
                                               String handle) throws IOException {
        return new BridgeDirectoryStream(((BridgePath) dir).getVirtualFile(), (BridgeFileSystem) dir.getFileSystem());
    }
}
