/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.EntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.db.es.IndexMappings;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which reindexes a given index in elastic.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ReindexJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private IndexMappings mappings;

    private final Parameter<EntityDescriptor> entityDescriptorParameter =
            new EntityDescriptorParameter().withFilter(EntityDescriptorParameter::isElasticEntity)
                                           .markRequired()
                                           .build();

    @Override
    public String getLabel() {
        return "Re-Index Elasticsearch Entity";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Creates a new index for the given entity and moves all data from the current index into the next one.";
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return Strings.apply("Reindexing mapping '%s'", entityDescriptorParameter.require(context).getRelationName());
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        EntityDescriptor ed = process.require(entityDescriptorParameter);
        String nextIndex = mappings.determineNextIndexName(ed);
        // Set the dynamic mapping mode to "false", so that legacy fields in documents are just ignored and don't
        // cause the reindex process to abort
        mappings.createMapping(ed, nextIndex, IndexMappings.DynamicMapping.FALSE);
        process.log("Created index: " + nextIndex);

        String currentIndex = elastic.determineEffectiveIndex(ed);
        String taskId = elastic.getLowLevelClient().startReindex(currentIndex, nextIndex);
        process.log("Started a reindex job in elasticsearch: " + taskId);

        Watch watch = Watch.start();
        while (TaskContext.get().isActive() && elastic.getLowLevelClient().checkTaskActivity(taskId)) {
            process.tryUpdateState("Reindex is still active (Runtime: " + watch.duration() + ")");
            Wait.seconds(5);
        }

        process.forceUpdateState("");

        if (!elastic.getLowLevelClient().checkTaskActivity(taskId)) {
            process.log(ProcessLog.success().withMessage("Reindex is complete! Runtime: " + watch.duration()));
        } else {
            process.log(ProcessLog.warn()
                                  .withFormattedMessage(
                                          "The task %s is still active in Elasticsearch. Use the Task API to kill manually!",
                                          taskId));
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(entityDescriptorParameter);
    }

    @Nonnull
    @Override
    public String getName() {
        return "reindex-index-mapping";
    }

    @Override
    public String getCategory() {
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }

}
