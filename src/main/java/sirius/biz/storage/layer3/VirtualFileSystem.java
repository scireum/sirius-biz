/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.storage.layer3.uplink.UplinkFactory;
import sirius.biz.storage.layer3.uplink.util.UplinkConnectorConfig;
import sirius.biz.storage.layer2.BlobStorageSpace;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.Optional;

/**
 * Provides the main entrypoint into the <b>Virtual File System</b>.
 * <p>
 * This serves mainly three purposes:
 * <ol>
 * <li>Making internally stored files accessible to the outside by using <b>downlinks</b> like FTP, SFTP or SCP</li>
 * <li>Making external files accessible to the software by using <b>uplinks</b> like FS, CIFS, SFTP</li>
 * <li>Provide a uniform management UI for all file based operations within sirius</li>
 * </ol>
 */
@Register(classes = VirtualFileSystem.class)
public class VirtualFileSystem {

    /**
     * Defines the name of the sub scope used by the {@link sirius.biz.storage.layer3.downlink.ftp.FTPServer} and
     * {@link sirius.biz.storage.layer3.downlink.ssh.SSHServer} which grants access per FTP, SFTP or SCP.
     */
    public static final String SUB_SCOPE_VFS = "vfs";
    private static final String CONFIG_TYPE = "type";

    /**
     * Provides a short default timeout of {@link #createTemporaryUplink(Function) temporary uplinks}.
     */
    private static final long DEFAULT_TEMPORARY_UPLINK_IDLE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    @Part
    private StorageUtils utils;

    @Part
    private GlobalContext globalContext;

    private VirtualFile root;

    @PriorityParts(VFSRoot.class)
    private List<VFSRoot> rootProviders;

    /**
     * Inline implementation which delegates all calls to the collected <tt>rootProviders</tt>.
     */
    private class RootProvider implements ChildProvider {

        @Override
        @Nullable
        public VirtualFile findChild(VirtualFile parent, String name) {
            for (VFSRoot vfsRoot : rootProviders) {
                VirtualFile result = vfsRoot.findChild(root(), name);
                if (result != null) {
                    return result;
                }
            }

            return null;
        }

        @Override
        public void enumerate(@Nonnull VirtualFile parent, FileSearch search) {
            rootProviders.forEach(vfsRoot -> vfsRoot.enumerate(root(), search));
        }
    }

    /**
     * Returns the root directory (<tt>/</tt>) wrapped as virtual file.
     *
     * @return the root directory of the virtual file system
     */
    public VirtualFile root() {
        if (root == null) {
            root = makeRoot();
        }

        return root;
    }

    private VirtualFile makeRoot() {
        return new MutableVirtualFile().markAsExistingDirectory()
                                       .withCanCreateChildren(MutableVirtualFile.CONSTANT_FALSE)
                                       .withChildren(new RootProvider());
    }

    /**
     * Resolves a path into a {@link VirtualFile}.
     * <p>
     * Note that the resolved file may not exist (yet).
     *
     * @param path the path to resolve.
     * @return the resolved file
     */
    @Nonnull
    public VirtualFile resolve(String path) {
        String sanitizedPath = utils.sanitizePath(path);

        if (Strings.isEmpty(sanitizedPath)) {
            return root();
        }

        return root().resolve(sanitizedPath);
    }

    /**
     * Creates a temporary (user-defined) uplink using the given configuration.
     * <p>
     * Note that the configuration must provide an entry for {@link #CONFIG_TYPE}. Also, it most probably should
     * provide settings for the configs defined in {@link sirius.biz.storage.layer3.uplink.util.UplinkConnectorConfig}
     * and maybe also those from {@link sirius.biz.storage.layer3.uplink.sftp.SFTPUplink} or
     * {@link sirius.biz.storage.layer3.uplink.ftp.FTPUplink}.
     *
     * @param config the provider for configuration settings. This could be based on a map like
     *               {@code key -> Value.of(map.get(key))}.
     * @return a {@link VirtualFile} representing the root of the mounted uplink. Note that this is an artificial
     * uplink which is not resolvable via {@link #resolve(String)}.
     */
    public VirtualFile createTemporaryUplink(Function<String, Value> config) {
        String generatedId = config.apply(CONFIG_TYPE).asString();
        try {
            return globalContext.findPart(generatedId, UplinkFactory.class)
                                .make(Strings.generateCode(32), provideShortIdleTimeout(config))
                                .getFile(root());
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 3: An error occurred while initializing a temporary uplink: %s")
                            .error(e)
                            .handle();
        }
    }

    /**
     * Injects a default handling which sets a shorter idle timeout for temporary uplinks.
     * <p>
     * If we're done using a temporary uplink, we can probably discard the connection pretty quickly and don't need
     * it to hang around for 10 mins.
     *
     * @param config the original config provider
     * @return an enhanced config provider which sets {@link UplinkConnectorConfig#CONFIG_IDLE_TIMEOUT_MILLIS} to
     * {@link #DEFAULT_TEMPORARY_UPLINK_IDLE_TIMEOUT} if no value is present.
     */
    private Function<String, Value> provideShortIdleTimeout(Function<String, Value> config) {
        return key -> {
            Value givenValue = config.apply(key);
            if (UplinkConnectorConfig.CONFIG_IDLE_TIMEOUT_MILLIS.equals(key) && !givenValue.isFilled()) {
                return Value.of(DEFAULT_TEMPORARY_UPLINK_IDLE_TIMEOUT);
            }

            return givenValue;
        };
    }

    /**
     * Builds a path from the given parts.
     * <p>
     * Builds a path like <tt>/foo/bar/baz</tt> for <tt>[foo, bar, baz]</tt>.
     *
     * @param parts the individual folder / file names to concatenate to a path
     * @return the absolute path built from the given parts
     */
    public String makePath(String... parts) {
        // Note that this is currently a very simple implementation but might be enhanced with additional
        // checks or cleanups...
        return "/" + Strings.join("/", parts);
    }

    /**
     * Checks if the file belongs to a storage space configured with retention days greater than zero.
     * <p>
     * This represents a file with a temporary character so the caller can use this information to decide
     * if a file can be deleted after being processed.
     *
     * @param virtualFile the virtual file to check
     * @return <tt>true</tt> when the owner space specifies retention days greater than zero, <tt>false</tt> otherwise
     */
    public boolean isAutoCleanupBlob(VirtualFile virtualFile) {
        Optional<BlobStorageSpace> blobStorageSpace = virtualFile.tryAs(BlobStorageSpace.class);

        if (blobStorageSpace.isEmpty()) {
            return false;
        }

        return blobStorageSpace.get().getRetentionDays() > 0;
    }
}
