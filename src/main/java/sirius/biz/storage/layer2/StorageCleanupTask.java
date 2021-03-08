/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.timer.EndOfDayTask;

/**
 * Removes old and outdated files from {@link BlobStorageSpace storage spaces}.
 *
 * @see BlobStorageSpace#runCleanup()
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class StorageCleanupTask implements EndOfDayTask {

    @Part
    private Tasks tasks;

    @Part
    private BlobStorage blobStorage;

    @Override
    public String getName() {
        return "storage-layer2-cleaner";
    }

    @Override
    public void execute() throws Exception {
        if (blobStorage != null) {
            try {
                blobStorage.getSpaces().forEach(BlobStorageSpace::runCleanup);
            } catch (Exception e) {
                Exceptions.handle(Log.BACKGROUND, e);
            }
        }
    }
}
