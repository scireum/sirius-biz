/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.ArchiveExportJob;
import sirius.biz.jobs.params.IntParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.s3.BucketName;
import sirius.biz.storage.s3.ObjectStores;
import sirius.kernel.async.ParallelTaskExecutor;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.CSVWriter;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * This job is used to verify the actual existence of blobs or variants for all physical IDs in the selected bucket.
 *
 */
public abstract class SearchOrphanS3ObjectsJob extends ArchiveExportJob {

    @Part
    private static ObjectStores objectStores;

    /**
     * The parameter name which is used to specify the bucket to check.
     */
    public static final String BUCKET_NAME_PARAMETER = "bucket";
    public static final String PARALLEL_TASKS_PARAMETER = "parallelTasksParameter";

    private static final int DEFAULT_PARALLEL_TASKS = 4;
    private static final int MAX_QUEUE_SIZE = 10000;

    protected CSVWriter writer;
    private final AtomicLong missingCount = new AtomicLong(0);
    private final AtomicLong missingSize = new AtomicLong(0);

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected SearchOrphanS3ObjectsJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected String determineFilenameWithoutExtension() {
        return getBucketName();
    }

    @Override
    public void execute() throws Exception {
        AtomicInteger total = new AtomicInteger(0);
        int parallelTasks = process.get(PARALLEL_TASKS_PARAMETER).asInt(DEFAULT_PARALLEL_TASKS);

        OutputStream outputStream = createEntry(getBucketName() + ".csv");
        writer = new CSVWriter(new OutputStreamWriter(outputStream));
        try {
            writeLine("bucketName", "physicalObjectId", "date", "size");
            // Creates a BucketName object. The suffix is already listed in the parameter.
            BucketName bucketName = new BucketName(getBucketName(), "");
            Queue<S3ObjectSummary> queue = new LinkedList<>();
            objectStores.store().listObjects(bucketName, null, object -> {
                process.tryUpdateState("Total: " + total.getAndIncrement());
                queue.add(object);
                if (queue.size() >= MAX_QUEUE_SIZE) {
                    drainQueue(queue, parallelTasks);
                }
                return process.isActive();
            });
            drainQueue(queue, parallelTasks);
        } finally {
            process.forceUpdateState("Total: " + total.get());
            if (missingCount.get() == 0) {
                process.log(ProcessLog.success().withMessage("No orphan objects found."));
            } else {
                process.log(ProcessLog.warn()
                                      .withFormattedMessage("Found %s orphan objects (%s)",
                                                            Amount.of(missingCount.get())
                                                                  .toString(NumberFormat.NO_DECIMAL_PLACES),
                                                            NLS.formatSize(missingSize.get())));
            }
            writer.close();
        }
    }

    private void drainQueue(Queue<S3ObjectSummary> queue, int parallelTasks) {
        ParallelTaskExecutor executor = new ParallelTaskExecutor(parallelTasks);
        while (!queue.isEmpty()) {
            S3ObjectSummary object = queue.poll();
            executor.submitTask(() -> checkObject(object));
        }
        executor.shutdownWhenDone();
    }

    protected abstract boolean blobExists(String physicalObjectKey);

    protected abstract boolean variantExists(String physicalObjectKey);

    private void checkObject(S3ObjectSummary object) {
        String physicalObjectKey = object.getKey();
        if (blobExists(physicalObjectKey) || variantExists(physicalObjectKey)) {
            return;
        }

        try {
            missingCount.incrementAndGet();
            missingSize.addAndGet(object.getSize());
            writeLine(getBucketName(),
                      physicalObjectKey,
                      LocalDateTime.ofInstant(object.getLastModified().toInstant(), ZoneId.systemDefault())
                                   .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                      object.getSize());
        } catch (IOException exception) {
            process.handle(exception);
        }
    }

    private String getBucketName() {
        return process.get(BUCKET_NAME_PARAMETER).asString();
    }

    private synchronized void writeLine(Object... columns) throws IOException {
        writer.writeArray(columns);
    }

    /**
     * A factory which is used to create new instances of {@link SearchOrphanS3ObjectsJob}.
     */
    public abstract static class SearchOrphanS3ObjectsJobFactory extends ArchiveExportJobFactory {

        @Part
        private ObjectStores objectStores;

        @Override
        protected String createProcessTitle(Map<String, String> context) {
            return getLabel() + ": " + context.get(SearchOrphanS3ObjectsJob.BUCKET_NAME_PARAMETER);
        }

        @Override
        protected PersistencePeriod getPersistencePeriod() {
            return PersistencePeriod.ONE_YEAR;
        }

        @Override
        protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
            SelectStringParameter bucketParameter =
                    new SelectStringParameter(BUCKET_NAME_PARAMETER, "Bucket").markRequired();
            objectStores.store().listBuckets().forEach(bucket -> bucketParameter.withEntry(bucket, bucket));
            parameterCollector.accept(bucketParameter.build());
            parameterCollector.accept(new IntParameter(PARALLEL_TASKS_PARAMETER, "Parallel Tasks").markRequired()
                                                                                                  .withDefault(
                                                                                                          DEFAULT_PARALLEL_TASKS)
                                                                                                  .withDescription(
                                                                                                          "Number of parallel tasks used to search for missing blobs.")
                                                                                                  .build());
        }

        @Override
        protected void collectAcceptedFileExtensions(Consumer<String> fileExtensionConsumer) {
            // not relevant
        }

        @Override
        public String getCategory() {
            return StandardCategories.SYSTEM_ADMINISTRATION;
        }

        @Override
        public int getPriority() {
            return 10700;
        }

        @Override
        public String getLabel() {
            return "Orphan S3 objects check";
        }

        @Nullable
        @Override
        public String getDescription() {
            return "Searches for orphan S3 objects, without a blob or variant counterpart.";
        }
    }
}
