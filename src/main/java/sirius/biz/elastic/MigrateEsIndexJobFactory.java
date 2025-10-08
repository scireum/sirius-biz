/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.db.es.IndexMappings;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Implements a job which migrates a given index or all indices in elastic.
 * It can be used when the elastic api "/_migration/deprecations?pretty" reports an old index with an incompatible
 * version. The elastic api "/_all?human&pretty" will return the version that created every index as "created_string".
 *
 * <p>
 * It creates a new index and reindex the existing data from the current index into the next one. During the reindex
 * process, the old index is still used for reading, but all new changes get written to the new index already.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class MigrateEsIndexJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private IndexMappings mappings;

    @Part
    private Mixing mixing;

    private final Parameter<EntityDescriptor> entityDescriptorParameter =
            new EntityDescriptorParameter().withFilter(EntityDescriptorParameter::isElasticEntity)
                                           .withDescription("If nothing is selected, all indices get migrated.")
                                           .build();

    private final Parameter<Boolean> deleteOldIndexParameter =
            new BooleanParameter("deleteOldIndex", "Delete Old Index").withDescription(
                    "If enabled, the old index will be deleted after a successful reindex.").withDefaultTrue().build();

    @Override
    public String getLabel() {
        return "Migrates Elasticsearch entities to new index";
    }

    @Nullable
    @Override
    public String getDescription() {
        return """
                Creates a new index for the given entity and reindex all data from the current index into the
                next one. Make sure that the elasticsearch cluster is in green state before starting this job
                and that enough disk space is available!""";
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return "Migrate index for " + entityDescriptorParameter.get(context)
                                                               .map(EntityDescriptor::getRelationName)
                                                               .orElse(" all entities");
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        Optional<EntityDescriptor> requestedEntity = process.getParameter(entityDescriptorParameter);
        List<EntityDescriptor> migratableEntities = requestedEntity.map(List::of)
                                                                   .orElseGet(() -> mixing.getDescriptors()
                                                                                          .stream()
                                                                                          .filter(EntityDescriptorParameter::isElasticEntity)
                                                                                          .toList());

        migratableEntities.forEach(ed -> migrateIndexForEntity(process, ed));
    }

    private void migrateIndexForEntity(ProcessContext process, EntityDescriptor entityDescriptor) {
        String oldIndex = elastic.determineEffectiveIndex(entityDescriptor);
        String nextIndex = mappings.determineNextIndexName(entityDescriptor);

        // Set the dynamic mapping mode to "false", so that legacy fields in documents are just ignored and don't
        // cause the reindex process to abort
        mappings.createMapping(entityDescriptor, nextIndex, IndexMappings.DynamicMapping.FALSE);
        elastic.installWriteIndex(entityDescriptor, nextIndex);
        process.log(ProcessLog.info()
                              .withFormattedMessage("Created new write index for %s: " + nextIndex,
                                                    entityDescriptor.getRelationName()));

        String reindexTaskId = elastic.getLowLevelClient().startReindex(oldIndex, nextIndex);
        process.log(ProcessLog.info()
                              .withFormattedMessage("Started a reindex job for %s in elasticsearch: " + reindexTaskId,
                                                    entityDescriptor.getRelationName()));

        Watch watch = Watch.start();
        while (TaskContext.get().isActive() && elastic.getLowLevelClient().checkTaskActivity(reindexTaskId)) {
            process.tryUpdateState(Strings.apply("Migration of %s is still active (Runtime: " + watch.duration() + ")",
                                                 entityDescriptor.getRelationName()));
            Wait.seconds(5);
        }

        process.forceUpdateState("");

        if (!elastic.getLowLevelClient().checkTaskActivity(reindexTaskId)) {
            // If successful, we set the use the write index as the new index
            elastic.commitWriteIndex(entityDescriptor);
            mappings.createMapping(entityDescriptor, nextIndex, IndexMappings.DynamicMapping.STRICT);

            if (process.require(deleteOldIndexParameter)) {
                elastic.getLowLevelClient().deleteIndex(oldIndex);
                process.log("Deleted old index: " + oldIndex);
            }

            process.log(ProcessLog.success()
                                  .withFormattedMessage("Migration of %s is complete! Runtime: " + watch.duration(),
                                                        entityDescriptor.getRelationName()));
        } else {
            process.log(ProcessLog.warn().withFormattedMessage("""
                                                                       The task %s is still active in Elasticsearch. Use the Task API to kill
                                                                       manually, reset the write index and delete the new index manually!""",
                                                               reindexTaskId));
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(entityDescriptorParameter);
        parameterCollector.accept(deleteOldIndexParameter);
    }

    @Nonnull
    @Override
    public String getName() {
        return "migrate-es-index";
    }

    @Override
    public int getPriority() {
        return 10195;
    }

    @Override
    public String getCategory() {
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }
}
