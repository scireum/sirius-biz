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

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides a minimal implementation of a {@link FileSystem} in order to bridge between the <b>SFTP server</b> and
 * our {@link VirtualFileSystem}.
 * <p>
 * Note that many methods throw an {@link UnsupportedOperationException} as they are (most probably) unused.
 */
public class BridgeFileSystem extends FileSystem {

    @Part
    private static VirtualFileSystem virtualFileSystem;

    private final BridgeFileSystemProvider provider = new BridgeFileSystemProvider();

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        // Nothing to release...
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        throw new UnsupportedOperationException("getRootDirectories");
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException("getFileStores");
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return new TreeSet<>(Arrays.asList("basic", "unix", "posix"));
    }

    @Override
    public Path getPath(String first, String... more) {
        if (Strings.isEmpty(first)) {
            return new BridgePath(virtualFileSystem.root(), this);
        }
        VirtualFile topLevelDirectory = virtualFileSystem.root().resolve(first);
        for (String part : more) {
            topLevelDirectory = topLevelDirectory.resolve(part);
        }
        topLevelDirectory.assertExists();
        return new BridgePath(topLevelDirectory, this);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("getPathMatcher");
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("getUserPrincipalLookupService");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("newWatchService");
    }
}
