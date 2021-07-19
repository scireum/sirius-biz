/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import sirius.biz.storage.layer3.VirtualFile;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Supports extracting attributes from a given {@link VirtualFile}.
 */
public class BridgePosixFileAttributes implements PosixFileAttributes {
    private final VirtualFile virtualFile;

    /**
     * Creates a new wrapper which extracts the attributes of the given file.
     *
     * @param virtualFile the file to extract the attributes from
     */
    public BridgePosixFileAttributes(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.from(virtualFile.lastModified(), TimeUnit.MILLISECONDS);
    }

    @Override
    public FileTime lastAccessTime() {
        return lastModifiedTime();
    }

    @Override
    public FileTime creationTime() {
        return lastModifiedTime();
    }

    @Override
    public boolean isRegularFile() {
        return !virtualFile.isDirectory() && virtualFile.exists();
    }

    @Override
    public boolean isDirectory() {
        return virtualFile.isDirectory() && virtualFile.exists();
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return virtualFile.size();
    }

    @Override
    public Object fileKey() {
        return virtualFile.path();
    }

    @Override
    public UserPrincipal owner() {
        return null;
    }

    @Override
    public GroupPrincipal group() {
        return null;
    }

    @Override
    public Set<PosixFilePermission> permissions() {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        if (virtualFile.isReadable()) {
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.OTHERS_READ);
        }

        if (virtualFile.isWriteable()) {
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.OTHERS_WRITE);
        }

        return perms;
    }
}
