/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import sirius.biz.storage.layer3.VirtualFile;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class BridgeFileSystemProvider extends FileSystemProvider {
    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        return null;
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return null;
    }

    @Override
    public Path getPath(URI uri) {
        return null;
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        return null;
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        return null;
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {

    }

    @Override
    public void delete(Path path) throws IOException {
       ((BridgePath) path).getVirtualFile().delete();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {

    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {

    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return false;
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return null;
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        VirtualFile file = ((BridgePath) path).getVirtualFile();
        for (AccessMode mode : modes) {
            if (mode == AccessMode.READ) {
                if (!(file.exists() && (file.isDirectory() || file.isReadable()))) {
                    //TODO
                }
            }
            if (mode == AccessMode.WRITE) {
                if (!(file.exists() && file.isReadable())) {
                    //TODO
                }
            }
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        return (A) new BridgeBasicFileAttributes(((BridgePath) path).getVirtualFile());
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        Map<String, Object> result = new HashMap<>();
        BridgeBasicFileAttributes attrs = readAttributes(path, BridgeBasicFileAttributes.class);
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

    }
}
