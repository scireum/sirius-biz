/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Forces the replication of a given {@link ObjectStorageSpace}.
 * <p>
 * This is most commonly used after as new replication target has been defined in order to copy the existing
 * objects.
 */
@Register
@Framework(StorageUtils.FRAMEWORK_STORAGE)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ForceReplicationJob extends SimpleBatchProcessJobFactory {

    private static final String PARAMETER_SPACE = "space";

    @Part
    private ObjectStorage objectStorage;

    @Part
    private ReplicationManager replicationManager;

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
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return NLS.fmtr("ForceReplicationJob.jobTitle").set(PARAMETER_SPACE, context.get(PARAMETER_SPACE)).format();
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        String spaceName = process.getContext().get(PARAMETER_SPACE);
        ObjectStorageSpace space = objectStorage.getSpace(spaceName);
        space.iterateObjects(key -> {
            try {
                Watch watch = Watch.start();
                replicationManager.notifyAboutUpdate(space, key, 0);
                process.addTiming("Object", watch.elapsedMillis());
            } catch (Exception e) {
                process.handle(e);
            }

            return true;
        });
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Nonnull
    @Override
    public String getName() {
        return "force-layer1-replicaion";
    }
}
