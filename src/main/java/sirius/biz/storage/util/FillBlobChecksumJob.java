/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.jobs.batch.BatchProcessJobFactory;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.IntParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.BlobStorageSpace;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.async.ParallelTaskExecutor;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This job is used to fill the checksum for {@linkplain Blob blobs} and {@linkplain BlobVariant variants}, where
 * a checksum algorithm was configured after the blob or variant was created.
 *
 * @param <B> the type of the blob being checked
 * @param <I> the type of the ID of the blob being checked
 */
public abstract class FillBlobChecksumJob<B extends BaseEntity<I> & Blob, I> extends BatchJob {

    @Part
    private static BlobStorage blobStorage;

    private static final int DEFAULT_PARALLEL_TASKS = 4;

    /**
     * The parameter name which specifies the storage space to be checked.
     */
    public static final String STORAGE_SPACE_PARAMETER = "space";
    public static final String PARALLEL_TASKS_PARAMETER = "parallelTasksParameter";
    public static final String START_FROM_ID_PARAMETER = "startFromId";

    protected BlobStorageSpace storageSpace;
    private final AtomicInteger totalBlobs = new AtomicInteger(0);
    private final AtomicInteger totalVariants = new AtomicInteger(0);
    private final AtomicInteger totalErrors = new AtomicInteger(0);

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected FillBlobChecksumJob(ProcessContext process) {
        super(process);
    }

    @Override
    public void execute() throws Exception {
        String spaceName = getStorageSpaceName();
        storageSpace = blobStorage.getSpace(spaceName);
        int parallelTasks = process.get(PARALLEL_TASKS_PARAMETER).asInt(DEFAULT_PARALLEL_TASKS);

        I lastId = null;
        I firstIdInBlock = null;
        while (TaskContext.get().isActive()) {
            List<B> blobs = fetchNextBlobBatch(lastId);
            if (blobs.isEmpty()) {
                break;
            }
            firstIdInBlock = fetchId(blobs.getFirst());

            ParallelTaskExecutor executor = new ParallelTaskExecutor(parallelTasks);
            for (B blob : blobs) {
                lastId = fetchId(blob);
                executor.submitTask(() -> processBlob(blob));
            }
            executor.shutdownWhenDone();
            process.tryUpdateState(buildStatusMessage());
        }
        process.forceUpdateState(buildStatusMessage());

        if (!TaskContext.get().isActive() && firstIdInBlock != null) {
            process.log(ProcessLog.warn()
                                  .withFormattedMessage("Job was cancelled. Resume it starting from ID: %s",
                                                        firstIdInBlock.toString()));
        }
    }

    private String buildStatusMessage() {
        return Strings.apply("Blobs: %s | Variants: %s | Errors: %s",
                             totalBlobs.get(),
                             totalVariants.get(),
                             totalErrors.get());
    }

    private <E extends BaseEntity<I>> I fetchId(E entity) {
        return entity.getId();
    }

    private void processBlob(B blob) {
        String blobChecksum = computeChecksum(blob.getPhysicalObjectKey());
        if (blobChecksum != null) {
            try {
                updateBlobChecksum(blob, blobChecksum);
                totalBlobs.incrementAndGet();
            } catch (Exception _) {
                totalErrors.incrementAndGet();
            }
        }
        blob.fetchVariants()
            .stream()
            .filter(variant -> Strings.isFilled(variant.getPhysicalObjectKey()))
            .filter(variant -> Strings.isEmpty(variant.getChecksum()))
            .forEach(variant -> {
                String variantChecksum = computeChecksum(variant.getPhysicalObjectKey());
                if (variantChecksum != null) {
                    totalVariants.incrementAndGet();
                    updateVariantChecksum(variant, variantChecksum);
                }
            });
    }

    private String computeChecksum(String physicalObjectKey) {
        Hasher hasher = storageSpace.getHasher();
        try (InputStream inputStream = storageSpace.getPhysicalSpace().getInputStream(physicalObjectKey).orElse(null)) {
            if (inputStream == null) {
                totalErrors.incrementAndGet();
                return null;
            }

            byte[] bytes = new byte[65536];
            int read;
            while ((read = inputStream.read(bytes)) != -1) {
                hasher.hashBytes(bytes, 0, read);
            }
            return hasher.toHexString();
        } catch (Exception exception) {
            // Errors are already logged by the framework
            Exceptions.ignore(exception);
            totalErrors.incrementAndGet();
            return null;
        }
    }

    protected abstract void updateBlobChecksum(B blob, String checksum);

    protected abstract void updateVariantChecksum(BlobVariant variant, String variantChecksum);

    protected abstract I fetchStartId();

    protected abstract List<B> fetchNextBlobBatch(I lastId);

    protected String getStorageSpaceName() {
        return process.get(STORAGE_SPACE_PARAMETER).asString();
    }

    /**
     * A factory which is used to create new instances of {@link FillBlobChecksumJob}.
     */
    public abstract static class FillBlobChecksumJobFactory extends BatchProcessJobFactory {

        @Part
        private BlobStorage blobStorage;

        @Override
        protected Class<? extends DistributedTaskExecutor> getExecutor() {
            return DefaultBatchProcessFactory.DefaultBatchProcessTaskExecutor.class;
        }

        @Override
        protected String createProcessTitle(Map<String, String> context) {
            return getLabel() + ": " + context.get(FillBlobChecksumJob.STORAGE_SPACE_PARAMETER);
        }

        @Override
        protected PersistencePeriod getPersistencePeriod() {
            return PersistencePeriod.THREE_MONTHS;
        }

        @Override
        protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
            SelectStringParameter spaceParameter =
                    new SelectStringParameter(STORAGE_SPACE_PARAMETER, "Storage Space").markRequired();
            blobStorage.getSpaces()
                       .filter(space -> space.getHasher() != null)
                       .map(BlobStorageSpace::getName)
                       .forEach(spaceName -> spaceParameter.withEntry(spaceName, spaceName));
            parameterCollector.accept(spaceParameter.withDescription("Storage space to scan for missing checksums.")
                                                    .build());
            parameterCollector.accept(new IntParameter(PARALLEL_TASKS_PARAMETER, "Parallel Tasks").markRequired()
                                                                                                  .withDefault(
                                                                                                          DEFAULT_PARALLEL_TASKS)
                                                                                                  .withDescription(
                                                                                                          "Number of parallel tasks used to compute empty checksums.")
                                                                                                  .build());
            parameterCollector.accept(new StringParameter(START_FROM_ID_PARAMETER, "Start from ID").withDescription(
                    "Optional blob ID to start from. Useful for restarting a previous cancelled job.").build());
        }

        @Override
        public String getCategory() {
            return StandardCategories.SYSTEM_ADMINISTRATION;
        }

        @Override
        public int getPriority() {
            return 10800;
        }

        @Override
        public String getLabel() {
            return "Fill Blob Checksums";
        }

        @Nullable
        @Override
        public String getDescription() {
            return "Computes and store missing checksums for blobs and variants.";
        }
    }
}
