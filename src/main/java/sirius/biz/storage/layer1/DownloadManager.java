/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.kernel.async.Promise;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Permits to download several objects from a {@link ObjectStorageSpace}.
 * <p>
 * If possible (and feasible) the downloads will be executed asynchronously to increase the overall speed. However,
 * the callbacks to the completion consumer will always happen from the thread which invokes {@link #close()} or
 * {@link #addDownload(Object, String)} so that no elaborate synchronization or thread safety is required.
 *
 * @param <P> the type of payload data which can be passed through to the handlers.
 */
public class DownloadManager<P> implements Closeable {

    private static final int MAX_QUEUED_DOWNLOADS = 8;
    private static final int COMPLETION_DURATION_SECONDS = 2;

    private final ObjectStorageSpace objectStorageSpace;
    private final BiConsumer<P, Optional<FileHandle>> completionConsumer;
    private final BiConsumer<P, Throwable> failureConsumer;
    private final List<Tuple<P, Promise<Optional<FileHandle>>>> downloadQueue = new ArrayList<>();

    protected DownloadManager(ObjectStorageSpace objectStorageSpace,
                              BiConsumer<P, Optional<FileHandle>> completionConsumer,
                              BiConsumer<P, Throwable> failureConsumer) {
        this.objectStorageSpace = objectStorageSpace;
        this.completionConsumer = completionConsumer;
        this.failureConsumer = failureConsumer;
    }

    /**
     * Adds a download for the given payload and object.
     *
     * @param payload  the payload to pass along to the callback
     * @param objectId the object to download
     */
    public void addDownload(P payload, String objectId) {
        List<Tuple<P, Optional<FileHandle>>> completedDownloads = new ArrayList<>();
        while (isDownloadQueueFull()) {
            pullCompletedDownloads(completedDownloads::add);
            if (isDownloadQueueFull()) {
                Wait.seconds(COMPLETION_DURATION_SECONDS);
            }
        }

        downloadQueue.add(Tuple.create(payload, objectStorageSpace.downloadAsync(objectId)));
        completedDownloads.forEach(download -> completionConsumer.accept(download.getFirst(), download.getSecond()));
    }

    private boolean isDownloadQueueFull() {
        return downloadQueue.size() >= MAX_QUEUED_DOWNLOADS;
    }

    private void pullCompletedDownloads(Consumer<Tuple<P, Optional<FileHandle>>> completedDownloadHandler) {
        Iterator<Tuple<P, Promise<Optional<FileHandle>>>> iterator = downloadQueue.iterator();
        while (iterator.hasNext()) {
            Tuple<P, Promise<Optional<FileHandle>>> nextDownload = iterator.next();
            if (nextDownload.getSecond().isSuccessful()) {
                Optional<FileHandle> fileHandle = nextDownload.getSecond().get();
                if (fileHandle.isPresent()) {
                    completedDownloadHandler.accept(Tuple.create(nextDownload.getFirst(), fileHandle));
                } else {
                    failureConsumer.accept(nextDownload.getFirst(), new FileNotFoundException());
                }
                iterator.remove();
            } else if (nextDownload.getSecond().isFailed()) {
                failureConsumer.accept(nextDownload.getFirst(), nextDownload.getSecond().getFailure());
                iterator.remove();
            }
        }
    }

    @Override
    public void close() throws IOException {
        while (!downloadQueue.isEmpty()) {
            pullCompletedDownloads(payloadAndFile -> completionConsumer.accept(payloadAndFile.getFirst(),
                                                                               payloadAndFile.getSecond()));
            if (!downloadQueue.isEmpty()) {
                Wait.seconds(COMPLETION_DURATION_SECONDS);
            }
        }
    }
}
