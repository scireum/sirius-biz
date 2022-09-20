/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.LocalDateParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Forces the replication of a given {@link ObjectStorageSpace}.
 * <p>
 * This is most commonly used after a new replication target has been defined in order to copy the existing
 * objects.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ForceReplicationJob extends SimpleBatchProcessJobFactory {

    private static final String PARAMETER_SPACE = "space";

    @Part
    private ObjectStorage objectStorage;

    @Part
    private ReplicationManager replicationManager;

    private static final Parameter<LocalDate> MIN_MODIFICATION_DATE_PARAMETER =
            new LocalDateParameter("minModificationDate", "$ForceReplicationJob.minModificationDate").withDescription(
                    "$ForceReplicationJob.minModificationDate.description").build();

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        SelectStringParameter spaceParameter =
                new SelectStringParameter(PARAMETER_SPACE, "$ForceReplicationJob.space").markRequired();
        objectStorage.getSpaces()
                     .stream()
                     .filter(ObjectStorageSpace::hasReplicationSpace)
                     .map(ObjectStorageSpace::getName)
                     .forEach(spaceName -> spaceParameter.withEntry(spaceName, spaceName));

        parameterCollector.accept(spaceParameter.build());
        parameterCollector.accept(MIN_MODIFICATION_DATE_PARAMETER);
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return NLS.fmtr("ForceReplicationJob.jobTitle").set(PARAMETER_SPACE, context.get(PARAMETER_SPACE)).format();
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        String spaceName = process.getContext().get(PARAMETER_SPACE);

        LocalDate minModificationDate = process.getParameter(MIN_MODIFICATION_DATE_PARAMETER).orElse(null);
        ObjectStorageSpace space = objectStorage.getSpace(spaceName);
        space.iterateObjects(metadata -> {
            try {
                if (metadata.getSize() == 0) {
                    // We cannot / do not want to backup empty files...
                    process.incCounter("Skipped");
                    process.debug(ProcessLog.info()
                                            .withFormattedMessage("Skipped empty object: %s", metadata.getKey()));
                } else if (minModificationDate == null || !metadata.getLastModified()
                                                                   .toLocalDate()
                                                                   .isBefore(minModificationDate)) {
                    Watch watch = Watch.start();
                    replicationManager.notifyAboutUpdate(space, metadata.getKey(), metadata.getSize());
                    process.addTiming("Scheduled", watch.elapsedMillis());
                } else {
                    process.incCounter("Ignored");
                    process.debug(ProcessLog.info()
                                            .withFormattedMessage("Ignored unmodified object: %s", metadata.getKey()));
                }
            } catch (Exception e) {
                process.handle(e);
            }

            return true;
        });
    }

    @Nonnull
    @Override
    public String getName() {
        return "force-layer1-replication";
    }

    @Override
    public int getPriority() {
        return 10500;
    }

    @Override
    public String getCategory() {
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }

}
