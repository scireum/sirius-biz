/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.PriorityParts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Defines a loop to process creation, renaming and deletion of {@link Blob blobs} and deletion of {@link Directory directories}.
 *
 * @see ProcessBlobsLoop
 **/
public abstract class ProcessBlobsLoop extends BackgroundLoop {

    private static final double FREQUENCY_EVERY_FIFTEEN_SECONDS = 1 / 15d;

    @PriorityParts(BlobCreatedRenamedHandler.class)
    protected List<BlobCreatedRenamedHandler> createdOrRenamedHandlers;

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
        AtomicInteger deletedDirectories = deleteDirectories();
        AtomicInteger deletedBlobs = deleteBlobs();
        AtomicInteger createdRenamedBlobs = processCreatedOrRenamedBlobs();

        if (deletedDirectories.get() == 0 && deletedBlobs.get() == 0 && createdRenamedBlobs.get() == 0) {
            return null;
        }

        return Strings.apply("Deleted %s directories and %s blobs. Processed %s new or renamed blobs.",
                             deletedDirectories.get(),
                             deletedBlobs.get(),
                             createdRenamedBlobs.get());
    }

    protected abstract AtomicInteger deleteBlobs();

    protected abstract AtomicInteger deleteDirectories();

    protected abstract AtomicInteger processCreatedOrRenamedBlobs();

    protected abstract void propagateDelete(Directory dir);
}
