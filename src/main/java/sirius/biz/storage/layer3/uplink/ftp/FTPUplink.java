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
import org.apache.commons.net.ftp.FTPFileFilter;
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
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.ValueHolder;
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

    private static final String FEATURE_MLSD = "MLSD";

    private final FTPUplinkConnectorConfig ftpConfig;
    private ValueHolder<Boolean> supportsMLSD;
    private RemotePath basePath;

    @Part
    private static UplinkConnectorPool connectorPool;

    private FTPUplink(Extension config) {
        super(config);
        this.ftpConfig = new FTPUplinkConnectorConfig(config);
        this.basePath = new RemotePath(config.get("basePath").asString("/"));
    }

    private boolean checkForMLSD(FTPClient client) {
        if (supportsMLSD == null) {
            supportsMLSD = performMLSDFeatureCheck(client);
        }

        return supportsMLSD.get();
    }

    private ValueHolder<Boolean> performMLSDFeatureCheck(FTPClient client) {
        try {
            return ValueHolder.of(client.hasFeature(FEATURE_MLSD));
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 3/FTP: Cannot determine MLSD support for uplink '%s' - %s (%s)",
                                              ftpConfig)
                      .handle();
            return ValueHolder.of(false);
        }
    }

    @Override
    @SuppressWarnings("squid:S3626")
    @Explain("False positive, the return is necessary to prevent retries if an attempt was successful.")
    protected void enumerateDirectoryChildren(@Nonnull VirtualFile parent, FileSearch search) {
        if (!parent.isDirectory()) {
            return;
        }

        RemotePath relativeParent = parent.as(RemotePath.class);
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                processListing(parent, search, relativeParent, connector);
                return;
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/FTP: Cannot iterate over children of '%s' in uplink '%s' - %s (%s)",
                                            parent,
                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }
    }

    private void processListing(@Nonnull VirtualFile parent,
                                FileSearch search,
                                RemotePath relativeParent,
                                UplinkConnector<FTPClient> connector) throws IOException {
        for (FTPFile file : list(connector.connector(), relativeParent, null)) {
            if (isUsable(file) && !search.processResult(wrap(parent, file, file.getName()))) {
                return;
            }
        }
    }

    private FTPFile[] list(FTPClient client, RemotePath relativeParent, FTPFileFilter ftpFileFilter)
            throws IOException {
        if (checkForMLSD(client)) {
            if (ftpFileFilter != null) {
                return client.mlistDir(relativeParent.getPath(), ftpFileFilter);
            } else {
                return client.mlistDir(relativeParent.getPath());
            }
        } else {
            if (ftpFileFilter != null) {
                return client.listFiles(relativeParent.getPath(), ftpFileFilter);
            } else {
                return client.listFiles(relativeParent.getPath());
            }
        }
    }

    private boolean isUsable(FTPFile entry) {
        return !".".equals(entry.getName()) && !"..".equals(entry.getName());
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

    private MutableVirtualFile wrap(VirtualFile parent, FTPFile file, String filename) {
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

    private Optional<FTPFile> fetchFTPFile(VirtualFile file) {
        FTPFile result = file.tryAs(FTPFile.class).orElse(null);
        if (result != null) {
            return Optional.of(result);
        }

        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                FTPFile[] ftpFiles = list(connector.connector(),
                                          file.parent().as(RemotePath.class),
                                          ftpFile -> Strings.areEqual(ftpFile.getName(), file.name()));
                if (ftpFiles.length > 0) {
                    file.attach(ftpFiles[0]);
                    return Optional.of(ftpFiles[0]);
                } else {
                    return Optional.empty();
                }
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage("Layer 3/FTP: Cannot resolve '%s' for uplink '%s' - %s (%s)",
                                                            file,
                                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }

        throw new IllegalStateException();
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

    private boolean isExistingFile(VirtualFile file) {
        return fetchFTPFile(file).map(FTPFile::isFile).orElse(false);
    }

    private long sizeSupplier(VirtualFile file) {
        return fetchFTPFile(file).map(FTPFile::getSize).orElse(0L);
    }

    private long lastModifiedSupplier(VirtualFile file) {
        return Math.max(0, fetchFTPFile(file).map(FTPFile::getTimestamp).map(Calendar::getTimeInMillis).orElse(0L));
    }

    private boolean canFastMoveHandler(VirtualFile file, VirtualFile newParent) {
        return !readonly && this.equals(file.tryAs(FTPUplink.class).orElse(null)) && this.equals(newParent.tryAs(
                FTPUplink.class).orElse(null));
    }

    private boolean fastMoveHandler(VirtualFile file, VirtualFile newParent) {
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                connector.connector()
                         .rename(file.as(RemotePath.class).getPath(),
                                 newParent.as(RemotePath.class).child(file.name()).getPath());
                return true;
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/FTP: Cannot move '%s' to '%s' in uplink '%s': %s (%s)",
                                            file,
                                            newParent,
                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }

        return false;
    }

    private boolean renameHandler(VirtualFile file, String newName) {
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                connector.connector()
                         .rename(file.as(RemotePath.class).getPath(),
                                 file.parent().as(RemotePath.class).child(newName).getPath());
                return true;
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/FTP: Cannot rename '%s' to '%s' in uplink '%s': %s (%s)",
                                            file,
                                            newName,
                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }

        return false;
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        String relativePath = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                return connector.connector().makeDirectory(relativePath);
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/FTP: Failed to create a directory for '%s' in uplink '%s': %s (%s)",
                                            relativePath,
                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }

        return false;
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
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                return connector.connector().deleteFile(relativePath);
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage("Layer 3/FTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                                            relativePath,
                                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }

        return false;
    }

    private boolean removeDirectory(String relativePath) {
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                return connector.connector().removeDirectory(relativePath);
            } catch (Exception e) {
                connector.forceClose();
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage("Layer 3/FTP: Failed to delete '%s' in uplink '%s': %s (%s)",
                                                            relativePath,
                                                            ftpConfig)
                                    .handle();
                }
            } finally {
                connector.safeClose();
            }
        }

        return false;
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        String path = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                InputStream rawStream = connector.connector().retrieveFileStream(path);
                if (rawStream == null) {
                    throw new IOException("Cannot retrieve an input stream!");
                }

                WatchableInputStream watchableInputStream = new WatchableInputStream(rawStream);
                watchableInputStream.getCompletionFuture()
                                    .then(() -> completePendingCommand(connector, path, "download"));
                return watchableInputStream;
            } catch (Exception e) {
                connector.forceClose();
                connector.safeClose();
                if (attempt.shouldThrow(e)) {
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
        }

        throw new IllegalStateException();
    }

    private void completePendingCommand(UplinkConnector<FTPClient> connector, String path, String command) {
        try {
            if (!connector.connector().completePendingCommand()) {
                Exceptions.handle()
                          .to(StorageUtils.LOG)
                          .withSystemErrorMessage("Layer 3/FTP: Failed to complete the %s for '%s' in uplink '%s'.",
                                                  command,
                                                  path,
                                                  ftpConfig)
                          .handle();
                connector.forceClose();
            }
        } catch (IOException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 3/FTP: Failed to complete the %s for '%s' in uplink '%s': %s (%s)",
                                              command,
                                              path,
                                              ftpConfig)
                      .handle();
            connector.forceClose();
        }

        connector.safeClose();
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        String path = file.as(RemotePath.class).getPath();
        for (Attempt attempt : Attempt.values()) {
            UplinkConnector<FTPClient> connector = connectorPool.obtain(ftpConfig);
            try {
                OutputStream rawStream = connector.connector().storeFileStream(path);
                if (rawStream == null) {
                    throw new IOException("Cannot retrieve an output stream!");
                }

                WatchableOutputStream watchableOutputStream = new WatchableOutputStream(rawStream);
                watchableOutputStream.getCompletionFuture()
                                     .then(() -> completePendingCommand(connector, path, "upload"));
                return watchableOutputStream;
            } catch (Exception e) {
                connector.forceClose();
                connector.safeClose();
                if (attempt.shouldThrow(e)) {
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

        throw new IllegalStateException();
    }
}
