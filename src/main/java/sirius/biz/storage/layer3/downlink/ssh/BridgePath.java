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
import sirius.kernel.di.std.Part;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Objects;

/**
 * Provides a path implementation which carries along the underlying {@link VirtualFile}.
 * <p>
 * Note that many methods throw an {@link UnsupportedOperationException} as they are (most probably) unused.
 */
public class BridgePath implements Path {

    @Part
    private static VirtualFileSystem vfs;

    private final VirtualFile virtualFile;
    private BridgeFileSystem fileSystem;

    /**
     * Creates a new instance wrapping the given virtual file.
     *
     * @param virtualFile the file to wrap
     */
    public BridgePath(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

    /**
     * Creates a new instance which wraps the virtual file and carries along the associated file system.
     *
     * @param virtualFile the file to wrap
     * @param fileSystem the associated file system
     */
    public BridgePath(VirtualFile virtualFile, BridgeFileSystem fileSystem) {
        this.virtualFile = virtualFile;
        this.fileSystem = fileSystem;
    }

    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public Path getRoot() {
        VirtualFile parent = virtualFile;
        while (parent.parent() != null) {
            parent = parent.parent();
        }
        return new BridgePath(parent, fileSystem);
    }

    @Override
    public Path getFileName() {
        return new StringPath(virtualFile.name());
    }

    @Override
    public Path getParent() {
        if (virtualFile.parent() == null) {
            return null;
        }

        return new BridgePath(virtualFile.parent(), fileSystem);
    }

    @Override
    public int getNameCount() {
        VirtualFile parent = virtualFile.parent();
        int count = 0;
        while (parent != null) {
            count++;
            parent = parent.parent();
        }
        return count;
    }

    @Override
    public Path getName(int index) {
        int nameCount = getNameCount();
        // use '-1' as we want to get the first name, e.g. for index=0 at /foo/bar/baz we want 'foo'
        int steps = nameCount - index - 1;

        VirtualFile parentFile = virtualFile;
        for (int i = 0; i < steps; i++) {
            parentFile = parentFile.parent();
        }

        return new StringPath(parentFile.name());
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        throw new UnsupportedOperationException("subpath");
    }

    @Override
    public boolean startsWith(Path other) {
        throw new UnsupportedOperationException("startsWith");
    }

    @Override
    public boolean startsWith(String other) {
        throw new UnsupportedOperationException("startsWith");
    }

    @Override
    public boolean endsWith(Path other) {
        throw new UnsupportedOperationException("endsWith");
    }

    @Override
    public boolean endsWith(String other) {
        throw new UnsupportedOperationException("endsWith");
    }

    @Override
    public Path normalize() {
        return this;
    }

    @Override
    public Path resolve(Path other) {
        return resolve(other.toString());
    }

    @Override
    public Path resolve(String other) {
        if (".".equals(other)) {
            return this;
        }

        if (Strings.isFilled(other) && other.endsWith("/.")) {
            other = other.substring(0, other.length() - 1);
        }

        if (Strings.isFilled(other) && other.startsWith("/")) {
            if (other.endsWith("..")) {
                return new BridgePath(vfs.resolve(other.substring(0, other.length() - 3)).parent(), fileSystem);
            } else {
                return new BridgePath(vfs.resolve(other), fileSystem);
            }
        }

        if ("..".equals(other)) {
            return new BridgePath(virtualFile.parent(), fileSystem);
        }

        return new BridgePath(virtualFile.resolve(other), fileSystem);
    }

    @Override
    public Path resolveSibling(Path other) {
        throw new UnsupportedOperationException("resolveSibling");
    }

    @Override
    public Path resolveSibling(String other) {
        throw new UnsupportedOperationException("resolveSibling");
    }

    @Override
    public Path relativize(Path other) {
        String thisPath = this + "/";
        String otherPath = other.toString();
        if (otherPath.startsWith(thisPath)) {
            return new StringPath(otherPath.substring(thisPath.length()));
        }

        return other;
    }

    @Override
    public URI toUri() {
        throw new UnsupportedOperationException("toUri");
    }

    @Override
    public Path toAbsolutePath() {
        return this;
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return this;
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("toFile");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        throw new UnsupportedOperationException("register");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("register");
    }

    @Override
    public Iterator<Path> iterator() {
        throw new UnsupportedOperationException("iterator");
    }

    @Override
    public int compareTo(Path other) {
        if (other instanceof BridgePath bridgePath) {
            return Objects.compare(virtualFile, bridgePath.virtualFile, VirtualFile::compareTo);
        }

        return 1;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    @Override
    public String toString() {
        return virtualFile.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other instanceof BridgePath bridgePath) {
            return Objects.equals(virtualFile, bridgePath.virtualFile);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return virtualFile.hashCode();
    }
}
