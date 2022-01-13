/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import sirius.biz.storage.layer3.VirtualFile;
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

/**
 * Provides yet another class required to bridge between the file system used by the SFTP API and our
 * {@link sirius.biz.storage.layer3.VirtualFileSystem}.
 */
class BridgeFileSystemAccessor implements SftpFileSystemAccessor {

    @Override
    public SeekableByteChannel openFile(SftpSubsystemProxy subsystem,
                                        FileHandle fileHandle,
                                        Path file,
                                        String handle,
                                        Set<? extends OpenOption> options,
                                        FileAttribute<?>... attrs) throws IOException {
        VirtualFile virtualFile = ((BridgePath) file).getVirtualFile();

        // Create as empty file if non-existent...
        if (!virtualFile.exists()) {
            virtualFile.createOutputStream().close();
        }

        return new BridgeSeekableByteChannel(virtualFile);
    }

    @Override
    public FileLock tryLock(SftpSubsystemProxy subsystem,
                            FileHandle fileHandle,
                            Path file,
                            String handle,
                            Channel channel,
                            long position,
                            long size,
                            boolean shared) throws IOException {
        throw new UnsupportedOperationException("tryLock");
    }

    @Override
    public void syncFileData(SftpSubsystemProxy subsystem,
                             FileHandle fileHandle,
                             Path file,
                             String handle,
                             Channel channel) throws IOException {
        throw new UnsupportedOperationException("syncFileData");
    }

    @Override
    public DirectoryStream<Path> openDirectory(SftpSubsystemProxy subsystem,
                                               DirectoryHandle dirHandle,
                                               Path dir,
                                               String handle) throws IOException {
        return new BridgeDirectoryStream(((BridgePath) dir).getVirtualFile(), (BridgeFileSystem) dir.getFileSystem());
    }
}
