/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

class BridgePosixFileAttributesView implements PosixFileAttributeView {

    private final Path path;
    private final BridgeFileSystemProvider fs;
    private final LinkOption[] options;

    BridgePosixFileAttributesView(Path path, BridgeFileSystemProvider fs, LinkOption[] options) {
        this.path = path;
        this.fs = fs;
        this.options = options;
    }

    @Override
    public String name() {
        return "posix";
    }

    @Override
    public UserPrincipal getOwner() throws IOException {
        return null;
    }

    @Override
    public void setOwner(UserPrincipal owner) throws IOException {
        // The owner cannot be changed...
    }

    @Override
    public PosixFileAttributes readAttributes() throws IOException {
        return fs.readAttributes(path, PosixFileAttributes.class, options);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        // The times cannot be changed...
    }

    @Override
    public void setPermissions(Set<PosixFilePermission> perms) throws IOException {
        // The permissions cannot be changed...
    }

    @Override
    public void setGroup(GroupPrincipal group) throws IOException {
        // The group cannot be changed...
    }
}
