/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.kernel.commons.Strings;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides a minimal implementation of a {@link FileSystemProvider} in order to bridge between the <b>SFTP server</b> and
 * our {@link VirtualFileSystem}.
 * <p>
 * Note that many methods throw an {@link UnsupportedOperationException} as they are (most probably) unused.
 */
class BridgeFileSystemProvider extends FileSystemProvider {

    @Override
    public String getScheme() {
        throw new UnsupportedOperationException("getScheme");
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        throw new UnsupportedOperationException("newFileSystem");
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException("getFileSystem");
    }

    @Override
    public Path getPath(URI uri) {
        throw new UnsupportedOperationException("getPath");
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        throw new UnsupportedOperationException("newByteChannel");
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        throw new UnsupportedOperationException("newDirectoryStream");
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        ((BridgePath) dir).getVirtualFile().createAsDirectory();
    }

    @Override
    public void delete(Path path) throws IOException {
        ((BridgePath) path).getVirtualFile().delete();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        ((BridgePath) source).getVirtualFile().transferTo(((BridgePath) target).getVirtualFile().parent()).copy();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        ((BridgePath) source).getVirtualFile().transferTo(((BridgePath) target).getVirtualFile().parent()).move();
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        throw new UnsupportedOperationException("isSameFile");
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("getFileStore");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        VirtualFile file = ((BridgePath) path).getVirtualFile();
        if (!file.exists()) {
            throw new NoSuchFileException(path.toString());
        }

        for (AccessMode mode : modes) {
            if (mode == AccessMode.READ && !(file.exists() && (file.isDirectory() || file.isReadable()))) {
                throw new IOException(Strings.apply("Read for '%s' denied", path.toString()));
            }
            if (mode == AccessMode.WRITE && !(file.exists() && file.isWriteable())) {
                throw new IOException(Strings.apply("Write for '%s' denied", path.toString()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (type == PosixFileAttributeView.class) {
            return (V) new BridgePosixFileAttributesView(path, this, options);
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        VirtualFile virtualFile = ((BridgePath) path).getVirtualFile();
        if (!virtualFile.exists()) {
            throw new NoSuchFileException(path.toString());
        }

        return (A) new BridgePosixFileAttributes(virtualFile);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        Map<String, Object> result = new HashMap<>();

        // These are reverse engineered constants which seem to simply match the method names...
        BridgePosixFileAttributes attrs = readAttributes(path, BridgePosixFileAttributes.class);
        result.put("lastModifiedTime", attrs.lastModifiedTime());
        result.put("lastAccessTime", attrs.lastAccessTime());
        result.put("owner", attrs.owner());
        result.put("group", attrs.group());
        result.put("creationTime", attrs.creationTime());
        result.put("isDirectory", attrs.isDirectory());
        result.put("isRegularFile", attrs.isRegularFile());
        result.put("isSymbolicLink", attrs.isSymbolicLink());
        result.put("permissions", attrs.permissions());

        return result;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        // We cannot change any attributes - therefore we simply ignore this...
    }
}
