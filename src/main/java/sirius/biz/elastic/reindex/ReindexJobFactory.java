/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic.reindex;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.JobCategory;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.BatchProcessJobFactory;
import sirius.biz.jobs.batch.DefaultBatchProcessTaskExecutor;
import sirius.biz.jobs.params.ElasticEntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.db.es.IndexMappings;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which reindexes a given index in elastic.
 */
@Register(classes = JobFactory.class)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
public class ReindexJobFactory extends BatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private IndexMappings mappings;

    private ElasticEntityDescriptorParameter entityDescriptorParameter =
            (ElasticEntityDescriptorParameter) new ElasticEntityDescriptorParameter("ed",
                                                                                    "$ReindexJobFactory.descriptorParameter")
                    .markRequired();

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return Strings.apply("Reindexing mapping '%s'", context.get("ed"));
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultBatchProcessTaskExecutor.class;
    }

    @Override
    protected void executeTask(ProcessContext process) throws Exception {
        EntityDescriptor ed = process.getParameter(entityDescriptorParameter)
                                     .orElseThrow(() -> Exceptions.handle()
                                                                  .withSystemErrorMessage(
                                                                          "Can't resolve entity-descriptor.")
                                                                  .handle());

        String nextIndex = determineNextIndexName(ed);
        mappings.createMapping(ed, nextIndex);
        process.log("Created index " + nextIndex);
        process.log(elastic.getLowLevelClient().reindex(ed, nextIndex).toJSONString());
    }

    private String determineNextIndexName(EntityDescriptor ed) {
        String nextIndexName = ed.getRelationName() + "-" + NLS.toMachineString(LocalDate.now());
        int run = 0;

        while (run++ < 10) {
            if (!elastic.getLowLevelClient().indexExists(nextIndexName)) {
                return nextIndexName;
            }
            nextIndexName = ed.getRelationName() + "-" + NLS.toMachineString(LocalDate.now()) + "-" + run;
        }

        throw Exceptions.handle().withSystemErrorMessage("Couldn't find a unique index name after 10 runs!").handle();
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(entityDescriptorParameter);
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return false;
    }

    @Override
    protected void computePresetFor(Object targetObject, Map<String, Object> preset) {
        // nothing to do yet
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_MISC;
    }

    @Nonnull
    @Override
    public String getName() {
        return "reindex-index-mapping";
    }
}
