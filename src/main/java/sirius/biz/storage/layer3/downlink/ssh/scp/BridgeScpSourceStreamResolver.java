/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.scp;

import org.apache.sshd.common.session.Session;
import org.apache.sshd.scp.common.ScpSourceStreamResolver;
import org.apache.sshd.scp.common.helpers.ScpTimestampCommandDetails;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.downlink.ssh.BridgePosixFileAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.Set;

/**
 * Provides a resolver implementation which provides an <tt>InputStream</tt> for a {@link VirtualFile}.
 */
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
        return new BridgePosixFileAttributes(virtualFile).permissions();
    }

    @Override
    public ScpTimestampCommandDetails getTimestamp() throws IOException {
        return new ScpTimestampCommandDetails(virtualFile.lastModified(), virtualFile.lastModified());
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
