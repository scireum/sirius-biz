/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.locks.Locks;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.layer2.variants.ConversionEngine;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.storage.util.WatchableInputStream;
import sirius.db.KeyGenerator;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Processor;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.settings.Extension;
import sirius.web.http.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Represents a base implementation for a layer 2 storage space which manages {@link Blob blobs} and
 * {@link Directory directories}.
 *
 * @param <B> the effective subclass of {@link Blob} used by a concrete subclass
 * @param <D> the effective subclass of {@link Directory} used by a concrete subclass
 * @param <B> the effective subclass of {@link BlobVariant} used by a concrete subclass
 */
public abstract class BasicBlobStorageSpace<B extends Blob & OptimisticCreate, D extends Directory & OptimisticCreate, V extends BlobVariant>
        implements BlobStorageSpace {

    /**
     * Contains the name of the config key used to determine if a space is browsable.
     */
    private static final String CONFIG_KEY_BROWSABLE = "browsable";

    /**
     * Contains the name of the config key used to determine if a space is readonly.
     */
    private static final String CONFIG_KEY_READONLY = "readonly";

    /**
     * Contains the name of the config key used to determine the base url to use when generating
     * delivery URLs for this space.
     */
    private static final String CONFIG_KEY_BASE_URL = "baseUrl";

    @Part
    protected static ObjectStorage objectStorage;

    @Part
    protected static KeyGenerator keyGenerator;

    @Part
    protected static StorageUtils utils;

    @Part
    private static Locks locks;

    @Part
    protected static ConversionEngine conversionEngine;

    @Part
    protected static Tasks tasks;

    protected final Extension config;
    protected String spaceName;
    protected boolean browsable;
    protected boolean readonly;
    protected String baseUrl;
    protected ObjectStorageSpace objectStorageSpace;

    /**
     * Creates a new instance by loading all settings from the given config section.
     *
     * @param spaceName the name of this space
     * @param config    the configuration to read the settings from
     */
    protected BasicBlobStorageSpace(String spaceName, Extension config) {
        this.spaceName = spaceName;
        this.config = config;
        this.browsable = config.get(CONFIG_KEY_BROWSABLE).asBoolean();
        this.readonly = config.get(CONFIG_KEY_READONLY).asBoolean();
        this.baseUrl = config.get(CONFIG_KEY_BASE_URL).getString();
    }
    public ObjectStorageSpace getPhysicalSpace() {
        if (objectStorageSpace == null) {
            objectStorageSpace = objectStorage.getSpace(spaceName);
        }
        return objectStorageSpace;
    }

    @Override
    public boolean isBrowsable() {
        return browsable;
    }

    @Override
    public boolean isReadonly() {
        return readonly;
    }

    @Override
    public Directory getRoot(String tenantId) {
        // Attempt a fast lookup without any locking as most probably the directory will exist...
        D directory = findRoot(tenantId);

        if (directory == null) {
            if (locks.tryLock("create-root-" + tenantId, Duration.ofSeconds(2))) {
                try {
                    // We need to perform the lookup again while holding the lock - who knows
                    // what other nodes did inbetween..
                    directory = findRoot(tenantId);
                    if (directory == null) {
                        directory = createRoot(tenantId);
                    }
                } finally {
                    locks.unlock("create-root-" + tenantId);
                }
            }
        }

        return fetchDirectoryById(directory.getIdAsString());
    }

    /**
     * Tries to find the root directory for the given tenant.
     *
     * @param tenantId the id of the tenant to find the root directory for
     * @return the root directory or <tt>null</tt> if it doesn't exist yet
     */
    @Nullable
    protected abstract D findRoot(String tenantId);

    /**
     * Creates the root directory for the given tenant.
     *
     * @param tenantId the id of the tenant to create the root directory for
     * @return the root directory for the given tenant
     */
    protected abstract D createRoot(String tenantId);

    @Nullable
    protected Directory fetchDirectoryById(String idAsString) {
        if (Strings.isEmpty(idAsString)) {
            return null;
        }

        return directoryByIdCache.get(idAsString, this::lookupDirectoryById);
    }

    /**
     * Performs a lookup to fetch the directory with the given id.
     *
     * @param idAsString the id of the directory represented as string
     * @return the directory with the given id or <tt>null</tt> if no such directory exists
     */
    @Nullable
    protected abstract D lookupDirectoryById(String idAsString);

    /**
     * Performs a download / fetch of the given blob to make its data locally accessible.
     *
     * @param blob the blob to fetch the data for
     * @return a file handle which makes the blob data accessible or an empty optional if no data was present.
     * Note that the {@link FileHandle} must be closed once the data has been processed to ensure proper cleanup.
     */
    public Optional<FileHandle> download(Blob blob) {
        if (Strings.isEmpty(blob.getPhysicalObjectId())) {
            return Optional.empty();
        }

        return getPhysicalSpace().download(blob.getPhysicalObjectId());
    }

    /**
     * Donloads the contents of the given blob and provides access via an {@link InputStream}.
     *
     * @param blob the blob to fetch the data for
     * @return an input stream to read and process the contents of the blob
     */
    public InputStream createInputStream(Blob blob) {
        FileHandle fileHandle = blob.download().filter(FileHandle::exists).orElse(null);
        if (fileHandle == null) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot obtain a file handle for %s (%s)",
                                                    blob.getBlobKey(),
                                                    blob.getFilename())
                            .handle();
        }

        try {
            WatchableInputStream result = new WatchableInputStream(fileHandle.getInputStream());
            result.getCompletionFuture().onSuccess(() -> fileHandle.close()).onFailure(e -> fileHandle.close());
            return result;
        } catch (FileNotFoundException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot obtain a file handle for %s (%s): %s (%s)",
                                                    blob.getBlobKey(),
                                                    blob.getFilename())
                            .handle();
        }
    }
}
