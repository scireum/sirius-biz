/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Defines a loop to process creation, renaming and deletion of {@link Blob blobs} and deletion of {@link Directory directories}.
 **/
public abstract class ProcessBlobChangesLoop extends BackgroundLoop {

    /**
     * Defines the block size used for queries to propagate and handle various change flags.
     */
    protected static final int CURSOR_LIMIT = 1024;

    private static final double FREQUENCY_EVERY_FIFTEEN_SECONDS = 1 / 15d;

    @PriorityParts(BlobCreatedRenamedHandler.class)
    private List<BlobCreatedRenamedHandler> createdOrRenamedHandlers;

    @PriorityParts(BlobParentChangedHandler.class)
    private List<BlobParentChangedHandler> parentChangedHandlers;

    @Nonnull
    @Override
    public String getName() {
        return "storage-layer2-process";
    }

    @Override
    public double maxCallFrequency() {
        return FREQUENCY_EVERY_FIFTEEN_SECONDS;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        AtomicInteger deletedDirectories = new AtomicInteger();
        AtomicInteger renamedDirectories = new AtomicInteger();
        AtomicInteger deletedBlobs = new AtomicInteger();
        AtomicInteger createdRenamedBlobs = new AtomicInteger();
        AtomicInteger parentChangedBlobs = new AtomicInteger();

        deleteDirectories(deletedDirectories::incrementAndGet);
        processRenamedDirectories(renamedDirectories::incrementAndGet);
        deleteBlobs(deletedBlobs::incrementAndGet);
        processParentChangedBlobs(parentChangedBlobs::incrementAndGet);
        processCreatedOrRenamedBlobs(createdRenamedBlobs::incrementAndGet);

        if (deletedDirectories.get()
            + renamedDirectories.get()
            + deletedBlobs.get()
            + createdRenamedBlobs.get()
            + parentChangedBlobs.get() == 0) {
            return null;
        }

        return Strings.apply(
                "Directories: deleted (%s), renamed (%s). Blobs: deleted (%s), created/renamed (%s), moved (%s)",
                deletedDirectories.get(),
                renamedDirectories.get(),
                deletedBlobs.get(),
                createdRenamedBlobs.get(),
                parentChangedBlobs.get());
    }

    protected void deletePhysicalObject(@Nonnull Blob blob) {
        if (Strings.isFilled(blob.getPhysicalObjectKey())) {
            blob.getStorageSpace().getPhysicalSpace().delete(blob.getPhysicalObjectKey());
        }
    }

    protected void invokeChangedOrDeletedHandlers(Blob blob) {
        createdOrRenamedHandlers.forEach(handler -> {
            try {
                handler.execute(blob);
            } catch (Exception e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: %s failed to process the changed blob %s (%s) in %s: (%s)",
                        handler.getClass().getSimpleName(),
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName()).handle();
            }
        });
    }

    protected void invokeParentChangedHandlers(Blob blob) {
        parentChangedHandlers.forEach(handler -> {
            try {
                handler.execute(blob);
            } catch (Exception e) {
                buildStorageException(e).withSystemErrorMessage(
                        "Layer 2: %s failed to process parent change for blob %s (%s) in %s: (%s)",
                        handler.getClass().getSimpleName(),
                        blob.getBlobKey(),
                        blob.getFilename(),
                        blob.getSpaceName()).handle();
            }
        });
    }

    protected void handleBlobDeletionException(@Nonnull Blob blob, Exception e) {
        buildStorageException(e).withSystemErrorMessage("Layer 2: Failed to finally delete the blob %s (%s) in %s: (%s)",
                                                        blob.getBlobKey(),
                                                        blob.getFilename(),
                                                        blob.getSpaceName()).handle();
    }

    protected void handleDirectoryDeletionException(@Nonnull Directory dir, Exception e) {
        buildStorageException(e).withSystemErrorMessage(
                "Layer 2: Failed to finally delete the directory %s (%s) in %s: (%s)",
                dir.getIdAsString(),
                dir.getName(),
                dir.getSpaceName()).handle();
    }

    protected void handleDirectoryRenameException(@Nonnull Directory dir, Exception e) {
        buildStorageException(e).withSystemErrorMessage(
                "Layer 2: Failed to process rename of directory %s (%s) in %s: (%s)",
                dir.getIdAsString(),
                dir.getName(),
                dir.getSpaceName()).handle();
    }

    protected Exceptions.ErrorHandler buildStorageException(Exception e) {
        return Exceptions.handle().to(StorageUtils.LOG).error(e);
    }

    /**
     * Queries and physically delete all {@link Blob blobs} marked as deleted.
     *
     * @param counter a {@link Runnable} to be called for each {@link Blob blob} deleted
     */
    protected abstract void deleteBlobs(Runnable counter);

    /**
     * Queries and physically delete all {@link Directory directories} marked as deleted.
     * <p>
     * Each directory is then processed by {@link #propagateDelete(Directory)}
     *
     * @param counter a {@link Runnable} to be called for each {@link Directory directory} deleted
     */
    protected abstract void deleteDirectories(Runnable counter);

    /**
     * Queries and processes {@link Blob blobs} marked as created or had the file name renamed.
     * <p>
     * The processing is performed by the registered {@link BlobCreatedRenamedHandler handlers}
     *
     * @param counter a {@link Runnable} to be called for each {@link Blob blob} processed
     */
    protected abstract void processCreatedOrRenamedBlobs(Runnable counter);

    /**
     * Marks children items of a given {@link Directory directory} as deleted.
     *
     * @param dir the parent {@link Directory} of the items to mark
     */
    protected abstract void propagateDelete(Directory dir);

    /**
     * Queries and processes {@link Directory directories} marked as renamed.
     * <p>
     * Each directory is then processed by {@link #propagateRename(Directory)}
     *
     * @param counter a {@link Runnable} to be called for each {@link Blob blob} processed
     */
    protected abstract void processRenamedDirectories(Runnable counter);

    /**
     * Notifies children items of a given {@link Directory directories} that its parent has been renamed.
     *
     * @param dir the parent {@link Directory} of the items to notify
     */
    protected abstract void propagateRename(Directory dir);

    /**
     * Queries and processes {@link Blob blobs} moved between {@link Directory directories}.
     * <p>
     * The processing is performed by the registered {@link BlobParentChangedHandler handlers}
     *
     * @param counter a {@link Runnable} to be called for each {@link Blob blob} processed
     */
    protected abstract void processParentChangedBlobs(Runnable counter);
}
