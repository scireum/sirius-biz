/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import com.rometools.utils.Strings;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.ArchiveExportJob;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.IntParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.layer2.Blob;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.async.ParallelTaskExecutor;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.CSVWriter;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This job is used to verify the actual existence of physical IDs of blobs for the selected {@linkplain ObjectStorageSpace storage space}.
 *
 * @param <B> the type of the blob being checked
 * @param <I> the type of the ID of the blob being checked
 */
public abstract class MissingBlobObjectCheckJob<B extends Blob, I> extends ArchiveExportJob {

    @Part
    private static ObjectStorage objectStorage;

    private static final int DEFAULT_PARALLEL_TASKS = 4;

    /**
     * The parameter name which specifies the storage space to be checked.
     */
    public static final String STORAGE_SPACE_PARAMETER = "space";
    public static final String INCLUDE_REPLICATION_SPACE_PARAMETER = "includeReplicationSpace";
    public static final String PARALLEL_TASKS_PARAMETER = "parallelTasksParameter";

    protected CSVWriter writer;
    protected ObjectStorageSpace storageSpace;
    private boolean includeReplicationSpace;

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected MissingBlobObjectCheckJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected String determineFilenameWithoutExtension() {
        return getStorageSpaceName();
    }

    @Override
    public void execute() throws Exception {
        String spaceName = getStorageSpaceName();
        storageSpace = objectStorage.getSpace(spaceName);
        includeReplicationSpace = process.get(INCLUDE_REPLICATION_SPACE_PARAMETER).asBoolean(false);
        int parallelTasks = process.get(PARALLEL_TASKS_PARAMETER).asInt(DEFAULT_PARALLEL_TASKS);
        OutputStream outputStream = createEntry(spaceName + ".csv");
        writer = new CSVWriter(new OutputStreamWriter(outputStream));
        try {
            writer.writeArray("id", "blobKey", "physicalObjectKey", "filename", "lastModified", "foundInReplication");

            I lastId = null;
            while (TaskContext.get().isActive()) {
                List<B> blobs = fetchNextBlobBatch(lastId);
                if (blobs.isEmpty()) {
                    break;
                }

                ParallelTaskExecutor executor = new ParallelTaskExecutor(parallelTasks);
                for (B blob : blobs) {
                    lastId = fetchBlobId(blob);
                    executor.submitTask(() -> processBlob(blob));
                }
                executor.shutdownWhenDone();
            }
        } finally {
            writer.close();
        }
    }

    @SuppressWarnings("unchecked")
    private I fetchBlobId(B blob) {
        return ((BaseEntity<I>) blob).getId();
    }

    protected void processBlob(B blob) {
        incrementCounter("total-blobs");
        String physicalObjectKey = blob.getPhysicalObjectKey();
        if (Strings.isEmpty(physicalObjectKey)) {
            return;
        }
        try {
            if (!storageSpace.exists(physicalObjectKey)) {
                incrementCounter("missing-blobs");
                writeLine(fetchBlobId(blob),
                          blob.getBlobKey(),
                          physicalObjectKey,
                          blob.getFilename(),
                          NLS.toMachineString(blob.getLastModified()),
                          existsInReplicationSpace(physicalObjectKey));
            }
        } catch (IOException exception) {
            process.handle(exception);
        }
    }

    protected abstract List<B> fetchNextBlobBatch(I lastId);

    protected String getStorageSpaceName() {
        return process.get(STORAGE_SPACE_PARAMETER).asString();
    }

    private synchronized void writeLine(Object... columns) throws IOException {
        writer.writeArray(columns);
    }

    private synchronized void incrementCounter(String counter) {
        process.incCounter(counter);
    }

    private String existsInReplicationSpace(String objectId) throws IOException {
        if (includeReplicationSpace && storageSpace.hasReplicationSpace()) {
            return String.valueOf(storageSpace.getReplicationSpace().exists(objectId));
        }
        return "-";
    }

    /**
     * A factory which is used to create new instances of {@link MissingBlobObjectCheckJob}.
     */
    public abstract static class MissingBlobObjectCheckJobFactory extends ArchiveExportJob.ArchiveExportJobFactory {

        @Part
        private ObjectStorage objectStorage;

        @Override
        protected String createProcessTitle(Map<String, String> context) {
            return getLabel() + ": " + context.get(MissingBlobObjectCheckJob.STORAGE_SPACE_PARAMETER);
        }

        @Override
        protected PersistencePeriod getPersistencePeriod() {
            return PersistencePeriod.ONE_YEAR;
        }

        @Override
        protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
            SelectStringParameter spaceParameter =
                    new SelectStringParameter(STORAGE_SPACE_PARAMETER, "Storage Space").markRequired();
            objectStorage.getSpaces()
                         .stream()
                         .map(ObjectStorageSpace::getName)
                         .forEach(spaceName -> spaceParameter.withEntry(spaceName, spaceName));
            parameterCollector.accept(spaceParameter.withDescription("Storage space to scan for missing objects.")
                                                    .build());
            parameterCollector.accept(new BooleanParameter(INCLUDE_REPLICATION_SPACE_PARAMETER,
                                                           "Include Replication Space").withDescription(
                    "Search for missing objects in the replication space, if one is configured.").build());
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
        public String getLabel() {
            return "Missing storage space objects check";
        }

        @Nullable
        @Override
        public String getDescription() {
            return "Checks if storage space objects associated to blobs are missing.";
        }
    }
}
