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
import sirius.kernel.commons.RateLimit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Aggregates to block-commits touch events.
 * <p>
 * This way we limit the max number of events being recorded, as in an overload condition, we rather want to simply
 * interrupt tracking instead of taking the whole system down.
 */
@Register(classes = {TouchWritebackLoop.class, BackgroundLoop.class}, framework = StorageUtils.FRAMEWORK_STORAGE)
public class TouchWritebackLoop extends BackgroundLoop {

    private final BlockingQueue<Tuple<String, String>> touchedBlobs = new LinkedBlockingQueue<>(32768);
    private final RateLimit logLimit = RateLimit.timeInterval(10, TimeUnit.MINUTES);

    @Part
    private BlobStorage blobStorage;

    /**
     * Queues a touch event for the given space and blob.
     *
     * @param space   the space which contains the blob
     * @param blobKey the key of the blob to mark as touched
     */
    public void markTouched(String space, String blobKey) {
        if (Strings.isEmpty(blobKey)) {
            return;
        }

        if (blobStorage == null) {
            return;
        }

        boolean added = touchedBlobs.offer(Tuple.create(space, blobKey));
        if (!added && logLimit.check()) {
            StorageUtils.LOG.WARN("Layer2: Dropping touch events as the internal queue is full!");
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "storage-layer2-touch-writeback";
    }

    @Override
    public double maxCallFrequency() {
        return 1d / 30;
    }

    @Override
    public double maxRuntimeInSeconds() {
        return 600d;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        Watch w = Watch.start();
        List<Tuple<String, String>> unitOfWork = new ArrayList<>();
        touchedBlobs.drainTo(unitOfWork);

        if (blobStorage == null) {
            return null;
        }

        Map<String, Set<String>> blobsPerSpace = unitOfWork.stream()
                                                           .collect(Collectors.groupingBy(Tuple::getFirst,
                                                                                          Collectors.mapping(Tuple::getSecond,
                                                                                                             Collectors.toSet())));

        blobsPerSpace.forEach((space, keys) -> {
            blobStorage.getSpace(space).markTouched(keys);
        });

        if (blobsPerSpace.isEmpty()) {
            return null;
        } else {
            return Strings.apply("Touched %s blobs in %s spaces within %s",
                                 unitOfWork.size(),
                                 blobsPerSpace.entrySet().size(),
                                 w.duration());
        }
    }
}
