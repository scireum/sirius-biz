/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.sftp;

import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplink;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplinkFactory;
import sirius.biz.storage.layer3.uplink.util.RemotePath;
import sirius.biz.storage.layer3.uplink.util.UplinkConnector;
import sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool;
import sirius.biz.storage.util.Attempt;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.storage.util.WatchableInputStream;
import sirius.biz.storage.util.WatchableOutputStream;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

/**
 * Provides an uplink which connects to a remote SFTP server.
 */
public class SFTPUplink extends ConfigBasedUplink {

    /**
     * Creates a new uplink for config sections which use "ftp" as type.
     */
    @Register
    public static class Factory implements ConfigBasedUplinkFactory {

        @Override
        public ConfigBasedUplink make(Extension config) {
            return new SFTPUplink(config);
        }

        @Nonnull
        @Override
        public String getName() {
            return "sftp";
        }
    }

    @Part
    private static UplinkConnectorPool connectorPool;

    private final SFTPUplinkConnectorConfig sftpConfig;
    private final RemotePath basePath;

    private SFTPUplink(Extension config) {
        super(config);
        this.sftpConfig = new SFTPUplinkConnectorConfig(config);
        this.basePath = new RemotePath(config.get("basePath").asString("/"));
    }

    @Override
    protected void enumerateDirectoryChildren(@Nonnull VirtualFile parent, FileSearch search) {
        if (!parent.isDirectory()) {
            return;
        }

        RemotePath remoteParent = parent.as(RemotePath.class);
        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                processListing(parent, search, remoteParent, connector);
                return;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Cannot iterate over children of '%s' in uplink '%s' - %s (%s)",
                                            parent,
                                            sftpConfig)
                                    .handle();
                }
            }
        }
    }

    private void processListing(@Nonnull VirtualFile parent,
                                FileSearch search,
                                RemotePath remoteParent,
                                UplinkConnector<SftpClient> connector) throws IOException {
        for (SftpClient.DirEntry entry : connector.connector().readDir(remoteParent.getPath())) {
            if (isUsable(entry) && !search.processResult(wrap(parent, entry, entry.getFilename()))) {
                return;
            }
        }
    }

    private boolean isUsable(SftpClient.DirEntry entry) {
        return !".".equals(entry.getFilename()) && !"..".equals(entry.getFilename());
    }

    @Override
    protected MutableVirtualFile createDirectoryFile(@Nonnull VirtualFile parent) {
        MutableVirtualFile mutableVirtualFile = MutableVirtualFile.checkedCreate(parent, getDirectoryName());
        mutableVirtualFile.attach(basePath);
        return mutableVirtualFile;
    }

    @Override
    @Nullable
    protected VirtualFile findChildInDirectory(VirtualFile parent, String name) {
        return wrap(parent, null, name);
    }

    private MutableVirtualFile wrap(VirtualFile parent, SftpClient.DirEntry file, String filename) {
        MutableVirtualFile result = createVirtualFile(parent, filename);

        result.withExistsFlagSupplier(this::existsFlagSupplier)
              .withDirectoryFlagSupplier(this::isDirectoryFlagSupplier)
              .withSizeSupplier(this::sizeSupplier)
              .withLastModifiedSupplier(this::lastModifiedSupplier)
              .withDeleteHandler(this::deleteHandler)
              .withCanProvideInputStream(this::isExistingFile)
              .withInputStreamSupplier(this::inputStreamSupplier)
              .withOutputStreamSupplier(this::outputStreamSupplier)
              .withRenameHandler(this::renameHandler)
              .withCreateDirectoryHandler(this::createDirectoryHandler)
              .withCanFastMoveHandler(this::canFastMoveHandler)
              .withFastMoveHandler(this::fastMoveHandler);

        result.attach(parent.as(RemotePath.class).child(filename));
        result.attach(this);

        if (file != null) {
            result.attach(file);
        }

        return result;
    }

    private boolean canFastMoveHandler(VirtualFile file, VirtualFile newParent) {
        return !readonly && this.equals(file.tryAs(SFTPUplink.class).orElse(null)) && this.equals(newParent.tryAs(
                SFTPUplink.class).orElse(null));
    }

    private boolean fastMoveHandler(VirtualFile file, VirtualFile newParent) {
        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector()
                         .rename(file.as(RemotePath.class).getPath(),
                                 newParent.as(RemotePath.class).child(file.name()).getPath());
                return true;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Cannot move '%s' to '%s' in uplink '%s': %s (%s)",
                                            file,
                                            newParent,
                                            sftpConfig)
                                    .handle();
                }
            }
        }

        return false;
    }

    private boolean renameHandler(VirtualFile file, String newName) {
        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector()
                         .rename(file.as(RemotePath.class).getPath(),
                                 file.parent().as(RemotePath.class).child(newName).getPath());
                return true;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Cannot rename '%s' to '%s' in uplink '%s': %s (%s)",
                                            file,
                                            newName,
                                            sftpConfig)
                                    .handle();
                }
            }
        }

        return false;
    }

    private SftpClient.Attributes getAttributes(VirtualFile file) {
        SftpClient.Attributes dirAttributes =
                file.tryAs(SftpClient.DirEntry.class).map(SftpClient.DirEntry::getAttributes).orElse(null);
        if (dirAttributes != null) {
            return dirAttributes;
        }

        Optional<SftpClient.Attributes> attributes = file.tryAs(SftpClient.Attributes.class);
        if (attributes.isPresent()) {
            return attributes.get();
        }

        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                SftpClient.Attributes stat = connector.connector().stat(file.as(RemotePath.class).getPath());
                file.attach(stat);
                return stat;
            } catch (SftpException e) {
                if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE
                    || e.getStatus() == SftpConstants.SSH_FX_PERMISSION_DENIED) {
                    return new SftpClient.Attributes();
                }

                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Layer 3/SFTP: Cannot determine the attributes of '%s' in uplink '%s': %s (%s)",
                                        file,
                                        sftpConfig)
                                .handle();
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Cannot determine the attributes of '%s' in uplink '%s': %s (%s)",
                                            file,
                                            sftpConfig)
                                    .handle();
                }
            }
        }

        throw new IllegalStateException();
    }

    private boolean existsFlagSupplier(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        return attributes.isDirectory() || attributes.isRegularFile();
    }

    private boolean isDirectoryFlagSupplier(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        return attributes.isDirectory();
    }

    private boolean isExistingFile(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        return attributes.isRegularFile();
    }

    private long sizeSupplier(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        return attributes.getSize();
    }

    private long lastModifiedSupplier(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        FileTime modifyTime = attributes.getModifyTime();
        return modifyTime == null ? 0 : modifyTime.toMillis();
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        String relativePath = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector().mkdir(relativePath);
                return true;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Failed to create a directory for '%s' in uplink '%s': %s (%s)",
                                            relativePath,
                                            sftpConfig)
                                    .handle();
                }
            }
        }

        return false;
    }

    private boolean deleteHandler(VirtualFile file) {
        if (file.isDirectory()) {
            deleteDirectoryHandler(file);
        } else {
            deleteFileHandler(file);
        }

        return true;
    }

    private void deleteFileHandler(VirtualFile file) {
        String relativePath = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector().remove(relativePath);
                return;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                            relativePath,
                                            sftpConfig)
                                    .handle();
                }
            }
        }
    }

    private void deleteDirectoryHandler(VirtualFile file) {
        String relativePath = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector().rmdir(relativePath);
                return;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                            relativePath,
                                            sftpConfig)
                                    .handle();
                }
            }
        }
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        String path = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig);
            try {
                InputStream rawStream = connector.connector().read(path);
                WatchableInputStream watchableInputStream = new WatchableInputStream(rawStream);
                watchableInputStream.getCompletionFuture().then(connector::safeClose);

                return watchableInputStream;
            } catch (Exception e) {
                connector.safeClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Failed to initiate a download for '%s' in uplink '%s': %s (%s)",
                                            path,
                                            sftpConfig)
                                    .handle();
                }
            }
        }

        throw new IllegalStateException();
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        String path = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig);
            try {
                OutputStream rawStream = connector.connector().write(path);
                WatchableOutputStream watchableOutputStream = new WatchableOutputStream(rawStream);
                watchableOutputStream.getCompletionFuture().then(connector::safeClose);

                return watchableOutputStream;
            } catch (Exception e) {
                connector.safeClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/SFTP: Failed to initiate an upload for '%s' in uplink '%s': %s (%s)",
                                            path,
                                            sftpConfig)
                                    .handle();
                }
            }
        }

        throw new IllegalStateException();
    }
}
