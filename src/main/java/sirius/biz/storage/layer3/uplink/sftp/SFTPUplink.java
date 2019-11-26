/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.sftp;

import org.apache.sshd.client.subsystem.sftp.SftpClient;
import org.apache.sshd.common.subsystem.sftp.SftpConstants;
import org.apache.sshd.common.subsystem.sftp.SftpException;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplink;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplinkFactory;
import sirius.biz.storage.layer3.uplink.util.RelativePath;
import sirius.biz.storage.layer3.uplink.util.UplinkConnector;
import sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.storage.util.WatchableInputStream;
import sirius.biz.storage.util.WatchableOutputStream;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
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

    private final SFTPUplinkConnectorConfig sftpConfig;

    @Part
    private static UplinkConnectorPool connectorPool;

    private SFTPUplink(Extension config) {
        super(config);
        this.sftpConfig = new SFTPUplinkConnectorConfig(config);
    }

    @Override
    protected void enumerateDirectoryChildren(@Nonnull VirtualFile parent, FileSearch search) {
        if (!parent.isDirectory()) {
            return;
        }

        RelativePath relativeParent = parent.as(RelativePath.class);
        try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
            for (SftpClient.DirEntry entry : connector.connector().readDir(relativeParent.getPath())) {
                if (isUsable(entry) && !search.processResult(wrap(parent, entry, entry.getFilename()))) {
                    return;
                }
            }
        } catch (Exception e) {
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

    private boolean isUsable(SftpClient.DirEntry entry) {
        return !".".equals(entry.getFilename()) && !"..".equals(entry.getFilename());
    }

    @Override
    protected MutableVirtualFile createDirectoryFile(@Nonnull VirtualFile parent) {
        MutableVirtualFile mutableVirtualFile = new MutableVirtualFile(parent, getDirectoryName());
        mutableVirtualFile.attach(new RelativePath("/"));
        return mutableVirtualFile;
    }

    @Override
    protected Optional<VirtualFile> findChildInDirectory(VirtualFile parent, String name) {
        return Optional.of(wrap(parent, null, name));
    }

    private MutableVirtualFile wrap(VirtualFile parent, SftpClient.DirEntry file, String filename) {
        MutableVirtualFile result = createVirtualFile(parent, filename);

        result.withExistsFlagSupplier(this::existsFlagSupplier)
              .withDirectoryFlagSupplier(this::isDirectoryFlagSupplier)
              .withSizeSupplier(this::sizeSupplier)
              .withLastModifiedSupplier(this::lastModifiedSupplier)
              .withDeleteHandler(this::deleteHandler)
              .withInputStreamSupplier(this::inputStreamSupplier)
              .withOutputStreamSupplier(this::outputStreamSupplier)
              .withRenameHandler(this::renameHandler)
              .withCreateDirectoryHandler(this::createDirectoryHandler)
              .withCanMoveHandler(this::canMoveHandler)
              .withMoveHandler(this::moveHandler);

        result.attach(parent.as(RelativePath.class).child(filename));
        if (file != null) {
            result.attach(file);
        }

        return result;
    }

    private boolean canMoveHandler(VirtualFile file) {
        //TODO SIRI-102 implement properly
        return false;
    }

    private boolean moveHandler(VirtualFile file, VirtualFile targetFile) {
        //TODO SIRI-102 implement properly
        return false;
    }

    private boolean renameHandler(VirtualFile file, String newName) {
        try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
            connector.connector()
                     .rename(file.as(RelativePath.class).getPath(),
                             file.parent().as(RelativePath.class).child(newName).getPath());
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/SFTP: Cannot rename '%s' to '%s' in uplink '%s': %s (%s)",
                                                    file,
                                                    newName,
                                                    sftpConfig)
                            .handle();
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

        try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
            SftpClient.Attributes stat = connector.connector().stat(file.as(RelativePath.class).getPath());
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

    private boolean existsFlagSupplier(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        return attributes.isDirectory() || attributes.isRegularFile();
    }

    private boolean isDirectoryFlagSupplier(VirtualFile file) {
        SftpClient.Attributes attributes = getAttributes(file);
        return attributes.isDirectory();
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
        String relativePath = file.as(RelativePath.class).getPath();
        try {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector().mkdir(relativePath);
                return true;
            }
        } catch (IOException e) {
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

    private boolean deleteHandler(VirtualFile file) {
        if (file.isDirectory()) {
            deleteDirectoryHandler(file);
        } else {
            deleteFileHandler(file);
        }

        return true;
    }

    private void deleteFileHandler(VirtualFile file) {
        String relativePath = file.as(RelativePath.class).getPath();
        try {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector().remove(relativePath);
            }
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/SFTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                                    relativePath,
                                                    sftpConfig)
                            .handle();
        }
    }

    private void deleteDirectoryHandler(VirtualFile file) {
        String relativePath = file.as(RelativePath.class).getPath();
        try {
            try (UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig)) {
                connector.connector().rmdir(relativePath);
            }
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/SFTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                                    relativePath,
                                                    sftpConfig)
                            .handle();
        }
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig);
        String path = file.as(RelativePath.class).getPath();
        try {
            InputStream rawStream = connector.connector().read(path);
            WatchableInputStream watchableInputStream = new WatchableInputStream(rawStream);
            watchableInputStream.getCompletionFuture().then(connector::safeClose);
            return watchableInputStream;
        } catch (IOException e) {
            connector.safeClose();
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

    private OutputStream outputStreamSupplier(VirtualFile file) {
        UplinkConnector<SftpClient> connector = connectorPool.obtain(sftpConfig);
        String path = file.as(RelativePath.class).getPath();
        try {
            OutputStream rawStream = connector.connector().write(path);
            WatchableOutputStream watchableOutputStream = new WatchableOutputStream(rawStream);
            watchableOutputStream.getCompletionFuture().then(connector::safeClose);
            return watchableOutputStream;
        } catch (IOException e) {
            connector.safeClose();
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
