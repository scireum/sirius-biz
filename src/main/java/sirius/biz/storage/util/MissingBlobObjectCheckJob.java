/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.ArchiveExportJob;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.kernel.commons.CSVWriter;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This job is used to verify the actual existence of physical IDs of blobs for the selected {@linkplain ObjectStorageSpace storage space}.
 */
public abstract class MissingBlobObjectCheckJob extends ArchiveExportJob {

    @Part
    private static ObjectStorage objectStorage;

    /**
     * The parameter name which specifies the storage space to be checked.
     */
    public static final String STORAGE_SPACE_PARAMETER = "space";

    protected CSVWriter writer;
    protected ObjectStorageSpace storageSpace;

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
        OutputStream outputStream = createEntry(spaceName + ".csv");
        writer = new CSVWriter(new OutputStreamWriter(outputStream));
        try {
            writer.writeArray("id", "blobKey", "physicalObjectKey", "filename", "lastModified");
        } finally {
            writer.close();
        }
    }

    private String getStorageSpaceName() {
        return process.get(STORAGE_SPACE_PARAMETER).asString();
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
                    new SelectStringParameter(MissingBlobObjectCheckJob.STORAGE_SPACE_PARAMETER,
                                              "Storage Space").markRequired();
            objectStorage.getSpaces()
                         .stream()
                         .map(ObjectStorageSpace::getName)
                         .forEach(spaceName -> spaceParameter.withEntry(spaceName, spaceName));
            parameterCollector.accept(spaceParameter.withDescription("Storage space to scan for missing objects.")
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
