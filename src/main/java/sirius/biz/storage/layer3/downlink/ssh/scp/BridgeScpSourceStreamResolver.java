/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.common.scp.ScpSourceStreamResolver;
import org.apache.sshd.common.scp.ScpTimestamp;
import org.apache.sshd.common.session.Session;
import sirius.biz.storage.layer3.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

class BridgeScpSourceStreamResolver implements ScpSourceStreamResolver {
    private final Path path;
    private final VirtualFile virtualFile;

    protected BridgeScpSourceStreamResolver(Path path, VirtualFile virtualFile) {
        this.path = path;
        this.virtualFile = virtualFile;
    }

    @Override
    public String getFileName() throws IOException {
        return virtualFile.name();
    }

    @Override
    public Path getEventListenerFilePath() {
        return path;
    }

    @Override
    public Collection<PosixFilePermission> getPermissions() throws IOException {
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

    @Override
    public ScpTimestamp getTimestamp() throws IOException {
        return new ScpTimestamp(virtualFile.lastModified(), virtualFile.lastModified());
    }

    @Override
    public long getSize() throws IOException {
        return virtualFile.size();
    }

    @Override
    public InputStream resolveSourceStream(Session session,
                                           long fileSize,
                                           Set<PosixFilePermission> permissions,
                                           OpenOption... options) throws IOException {
        return virtualFile.createInputStream();
    }
}
