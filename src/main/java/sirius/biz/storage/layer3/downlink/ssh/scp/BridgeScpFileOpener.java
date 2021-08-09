/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.common.session.Session;
import org.apache.sshd.scp.common.ScpFileOpener;
import org.apache.sshd.scp.common.ScpSourceStreamResolver;
import org.apache.sshd.scp.common.ScpTargetStreamResolver;
import org.apache.sshd.scp.common.helpers.ScpTimestampCommandDetails;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.biz.storage.layer3.downlink.ssh.BridgeDirectoryStream;
import sirius.biz.storage.layer3.downlink.ssh.BridgeFileSystem;
import sirius.biz.storage.layer3.downlink.ssh.BridgePath;
import sirius.biz.storage.layer3.downlink.ssh.BridgePosixFileAttributes;
import sirius.kernel.di.std.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provide some mappings between the <i>interesting</i> SCP implementation and our {@link VirtualFileSystem}.
 * <p>
 * Note that some methods are unimplemented and will throw an {@link UnsupportedOperationException}. These methods
 * were never invoked during testing and therefore left out.
 */
class BridgeScpFileOpener implements ScpFileOpener {

    @Part
    private static VirtualFileSystem vfs;

    @Override
    public Path resolveIncomingFilePath(Session session,
                                        Path localPath,
                                        String name,
                                        boolean preserve,
                                        Set<PosixFilePermission> permissions,
                                        ScpTimestampCommandDetails time) throws IOException {
        if (!((BridgePath) localPath).getVirtualFile().exists()) {
            throw new IOException(localPath + ": no such file or directory");
        }

        return localPath;
    }

    @Override
    public Iterable<Path> getMatchingFilesToSend(Session session, Path basedir, String pattern) throws IOException {
        VirtualFile baseDirAsFile = vfs.resolve(basedir.toAbsolutePath().toString());

        List<Path> result = new ArrayList<>();
        baseDirAsFile.children(FileSearch.iterateAll(child -> result.add(new BridgePath(child)))
                                         .withPrefixFilter(pattern));

        return result;
    }

    @Override
    public boolean sendAsRegularFile(Session session, Path path, LinkOption... options) throws IOException {
        VirtualFile virtualFile = ((BridgePath) path).getVirtualFile();
        return virtualFile.exists() && !virtualFile.isDirectory();
    }

    @Override
    public boolean sendAsDirectory(Session session, Path path, LinkOption... options) throws IOException {
        VirtualFile virtualFile = ((BridgePath) path).getVirtualFile();
        return virtualFile.exists() && virtualFile.isDirectory();
    }

    @Override
    public DirectoryStream<Path> getLocalFolderChildren(Session session, Path path) throws IOException {
        return new BridgeDirectoryStream(((BridgePath) path).getVirtualFile(), (BridgeFileSystem) path.getFileSystem());
    }

    @Override
    public BasicFileAttributes getLocalBasicFileAttributes(Session session, Path path, LinkOption... options)
            throws IOException {
        VirtualFile virtualFile = ((BridgePath) path).getVirtualFile();
        return new BridgePosixFileAttributes(virtualFile);
    }

    @Override
    public Set<PosixFilePermission> getLocalFilePermissions(Session session, Path path, LinkOption... options)
            throws IOException {
        VirtualFile virtualFile = ((BridgePath) path).getVirtualFile();
        return new BridgePosixFileAttributes(virtualFile).permissions();
    }

    @Override
    public Path resolveLocalPath(Session session, FileSystem fileSystem, String commandPath) throws IOException {
        return new BridgePath(vfs.resolve(commandPath));
    }

    @Override
    public Path resolveIncomingReceiveLocation(Session session,
                                               Path path,
                                               boolean recursive,
                                               boolean shouldBeDir,
                                               boolean preserve) throws IOException {
        if (!shouldBeDir) {
            return path;
        }

        VirtualFile virtualFile = ((BridgePath) path).getVirtualFile();
        if (!virtualFile.exists()) {
            throw new IOException(path + ": no such directory");
        }
        if (!virtualFile.isDirectory()) {
            throw new IOException(path + ": isn't a directory");
        }

        return path;
    }

    @Override
    public Path resolveOutgoingFilePath(Session session, Path localPath, LinkOption... options) throws IOException {
        if (!((BridgePath) localPath).getVirtualFile().exists()) {
            throw new IOException(localPath + ": no such file or directory");
        }

        return localPath;
    }

    @Override
    public InputStream openRead(Session session,
                                Path file,
                                long size,
                                Set<PosixFilePermission> permissions,
                                OpenOption... options) throws IOException {
        throw new UnsupportedOperationException("openRead");
    }

    @Override
    public ScpSourceStreamResolver createScpSourceStreamResolver(Session session, Path path) throws IOException {
        return new BridgeScpSourceStreamResolver(path, ((BridgePath) path).getVirtualFile());
    }

    @Override
    public OutputStream openWrite(Session session,
                                  Path file,
                                  long size,
                                  Set<PosixFilePermission> permissions,
                                  OpenOption... options) throws IOException {
        throw new UnsupportedOperationException("openWrite");
    }

    @Override
    public ScpTargetStreamResolver createScpTargetStreamResolver(Session session, Path path) throws IOException {
        return new BridgeScpTargetStreamResolver(path, ((BridgePath) path).getVirtualFile());
    }
}
