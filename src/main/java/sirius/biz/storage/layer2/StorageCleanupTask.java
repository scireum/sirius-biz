/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.timer.EveryDay;

/**
 * Removes old and outdated files from {@link BlobStorageSpace storage spaces}.
 *
 * @see BlobStorageSpace#runCleanup()
 */
@Register(classes = EveryDay.class)
public class StorageCleanupTask implements EveryDay {

    @Part
    private Tasks tasks;

    @Part
    private BlobStorage blobStorage;

    @Override
    public String getConfigKeyName() {
        return "storage-layer2-cleaner";
    }

    @Override
    public void runTimer() throws Exception {
        if (blobStorage == null) {
            return;
        }

        tasks.defaultExecutor().start(this::runCleanup);
    }

    protected void runCleanup() {
        try {
            blobStorage.getSpaces().forEach(BlobStorageSpace::runCleanup);
        } catch (Exception e) {
            Exceptions.handle(Log.BACKGROUND, e);
        }
    }
}
