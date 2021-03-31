/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Timeout;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
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
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultBatchProcessFactory.DefaultPartialBatchProcessTaskExecutor.class;
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        EntityDescriptor ed = process.require(entityDescriptorParameter);
        String nextIndex = mappings.determineNextIndexName(ed);
        // Set the dynamic mapping mode to "false", so that legacy fields in documents are just ignored and don't
        // cause the reindex process to abort
        mappings.createMapping(ed, nextIndex, IndexMappings.DynamicMapping.FALSE);
        process.log("Created index: " + nextIndex);

        Timeout firstStepCompleted = new Timeout(Duration.ofSeconds(3));
        String currentIndex = elastic.determineEffectiveIndex(ed);
        elastic.getLowLevelClient()
               .reindex(currentIndex,
                        nextIndex,
                        response -> handleSuccess(process.getProcessId(), firstStepCompleted),
                        exception -> handleFailure(exception, process.getProcessId(), firstStepCompleted));
        process.log("Started a reindex job in elasticsearch, check the task API to see the progress ...");
    }

    private void handleSuccess(String processId, Timeout firstStepCompleted) {
        // If the re-index was super quick (took less than 3 seconds), we
        // rather wait some seconds, as the process API is not meant to handle
        // fully concurrent updates...
        if (firstStepCompleted.notReached()) {
            Wait.seconds(3);
        }
        processes.execute(processId, processContext -> {
            processContext.log(ProcessLog.success().withMessage("Reindex completed!"));
        });
    }

    private void handleFailure(Exception exception, String processId, Timeout firstStepCompleted) {
        // If the re-index was super quick (took less than 3 seconds), we
        // rather wait some seconds, as the process API is not meant to handle
        // fully concurrent updates...
        if (firstStepCompleted.notReached()) {
            Wait.seconds(3);
        }
        processes.execute(processId, processContext -> {
            processContext.handle(exception);
        });
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
}
