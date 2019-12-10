/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplink;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplinkFactory;
import sirius.biz.storage.layer3.uplink.util.RemotePath;
import sirius.biz.storage.layer3.uplink.util.UplinkConnector;
import sirius.biz.storage.layer3.uplink.util.UplinkConnectorPool;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.storage.util.WatchableInputStream;
import sirius.biz.storage.util.WatchableOutputStream;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Optional;

/**
 * Provides an uplink which connects to a remote FTP server.
 */
public class FTPUplink extends ConfigBasedUplink {

    /**
     * Creates a new uplink for config sections which use "ftp" as type.
     */
    @Register
    public static class Factory implements ConfigBasedUplinkFactory {

        @Override
        public ConfigBasedUplink make(Extension config) {
            return new FTPUplink(config);
        }

        @Nonnull
        @Override
        public String getName() {
            return "ftp";
        }
    }

    private final FTPUplinkConnectorConfig ftpConfig;

    @Part
    private static UplinkConnectorPool connectorPool;

    private FTPUplink(Extension config) {
        super(config);
        this.ftpConfig = new FTPUplinkConnectorConfig(config);
    }

    @Override
    protected void enumerateDirectoryChildren(@Nonnull VirtualFile parent, FileSearch search) {
        if (!parent.isDirectory()) {
            return;
        }

        RemotePath relativeParent = parent.as(RemotePath.class);
        try (UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig)) {
            for (FTPFile file : connector.connector().listFiles(relativeParent.getPath())) {
                if (!search.processResult(wrap(parent, file, file.getName()))) {
                    return;
                }
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: Cannot iterate over children of '%s' in uplink '%s' - %s (%s)",
                                    parent,
                                    ftpConfig)
                            .handle();
        }
    }

    @Override
    protected MutableVirtualFile createDirectoryFile(@Nonnull VirtualFile parent) {
        MutableVirtualFile mutableVirtualFile = new MutableVirtualFile(parent, getDirectoryName());
        mutableVirtualFile.attach(new RemotePath("/"));
        return mutableVirtualFile;
    }

    @Override
    @Nullable
    protected VirtualFile findChildInDirectory(VirtualFile parent, String name) {
        return wrap(parent, null, name);
    }

    private MutableVirtualFile wrap(VirtualFile parent, FTPFile file, String filename) {
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

        result.attach(parent.as(RemotePath.class).child(filename));
        if (file != null) {
            result.attach(file);
        }

        return result;
    }

    private Optional<FTPFile> fetchFTPFile(VirtualFile file) {
        FTPFile result = file.tryAs(FTPFile.class).orElse(null);
        if (result != null) {
            return Optional.of(result);
        }

        try (UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig)) {
            FTPFile[] ftpFiles = connector.connector()
                                          .listFiles(file.parent().as(RemotePath.class).getPath(),
                                                     ftpFile -> Strings.areEqual(ftpFile.getName(), file.name()));
            if (ftpFiles.length == 1) {
                file.attach(ftpFiles[0]);
                return Optional.of(ftpFiles[0]);
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FTP: Cannot resolve '%s' for uplink '%s' - %s (%s)",
                                                    file,
                                                    ftpConfig)
                            .handle();
        }
    }

    private boolean existsFlagSupplier(VirtualFile file) {
        if (!file.parent().is(RemotePath.class)) {
            return true;
        }

        return fetchFTPFile(file).isPresent();
    }

    private boolean isDirectoryFlagSupplier(VirtualFile file) {
        return fetchFTPFile(file).map(FTPFile::isDirectory).orElse(false);
    }

    private long sizeSupplier(VirtualFile file) {
        return fetchFTPFile(file).map(FTPFile::getSize).orElse(0L);
    }

    private long lastModifiedSupplier(VirtualFile file) {
        return fetchFTPFile(file).map(FTPFile::getTimestamp).map(Calendar::getTimeInMillis).orElse(0L);
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
        try (UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig)) {
            connector.connector()
                     .rename(file.as(RemotePath.class).getPath(),
                             file.parent().as(RemotePath.class).child(newName).getPath());
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FTP: Cannot rename '%s' to '%s' in uplink '%s': %s (%s)",
                                                    file,
                                                    newName,
                                                    ftpConfig)
                            .handle();
        }

        return false;
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        String relativePath = file.as(RemotePath.class).getPath();

        if (file.parent() == null) {
            return true;
        }

        file.parent().createAsDirectory();

        try {
            try (UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig)) {
                return connector.connector().makeDirectory(relativePath);
            }
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: Failed to create a directory for '%s' in uplink '%s': %s (%s)",
                                    relativePath,
                                    ftpConfig)
                            .handle();
        }
    }

    private boolean deleteHandler(VirtualFile file) {
        String relativePath = file.as(RemotePath.class).getPath();
        if (file.isDirectory()) {
            return removeDirectory(relativePath);
        } else {
            return deleteFile(relativePath);
        }
    }

    private boolean deleteFile(String relativePath) {
        try {
            try (UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig)) {
                return connector.connector().deleteFile(relativePath);
            }
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                                    relativePath,
                                                    ftpConfig)
                            .handle();
        }
    }

    private boolean removeDirectory(String relativePath) {
        try {
            try (UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig)) {
                return connector.connector().removeDirectory(relativePath);
            }
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                                    relativePath,
                                                    ftpConfig)
                            .handle();
        }
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
        String path = file.as(RemotePath.class).getPath();
        try {
            InputStream rawStream = connector.connector().retrieveFileStream(path);
            WatchableInputStream watchableInputStream = new WatchableInputStream(rawStream);
            watchableInputStream.getCompletionFuture().then(connector::safeClose);
            return watchableInputStream;
        } catch (IOException e) {
            connector.safeClose();
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: Failed to initiate a download for '%s' in uplink '%s': %s (%s)",
                                    path,
                                    ftpConfig)
                            .handle();
        }
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
        String path = file.as(RemotePath.class).getPath();
        try {
            OutputStream rawStream = connector.connector().storeFileStream(path);
            WatchableOutputStream watchableOutputStream = new WatchableOutputStream(rawStream);
            watchableOutputStream.getCompletionFuture().then(connector::safeClose);
            return watchableOutputStream;
        } catch (IOException e) {
            connector.safeClose();
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 3/FTP: Failed to initiate an upload for '%s' in uplink '%s': %s (%s)",
                                    path,
                                    ftpConfig)
                            .handle();
        }
    }
}
