/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic.reindex;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.BatchProcessJobFactory;
import sirius.biz.jobs.batch.DefaultBatchProcessTaskExecutor;
import sirius.biz.jobs.params.ElasticEntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.ProcessContext;
import sirius.db.es.Elastic;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which moves the alias which marks an active index to a desired destination index.
 */
@Register(classes = JobFactory.class)
public class MoveIndexAliasJobFactory extends BatchProcessJobFactory {
    @Part
    private Elastic elastic;

    private ElasticEntityDescriptorParameter entityDescriptorParameter = null;
    private StringParameter stringParameter = null;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return "MoveIndexAlias";
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultBatchProcessTaskExecutor.class;
    }

    @Override
    protected void executeTask(ProcessContext process) throws Exception {
        String destination = process.getParameter(stringParameter)
                                    .orElseThrow(() -> Exceptions.handle()
                                                                 .withSystemErrorMessage("No destination index given!")
                                                                 .handle());
        EntityDescriptor ed = process.getParameter(entityDescriptorParameter)
                                     .orElseThrow(() -> Exceptions.handle()
                                                                  .withSystemErrorMessage(
                                                                          "Can't resolve entity-descriptor.")
                                                                  .handle());

        process.log(elastic.getLowLevelClient().moveActiveAlias(ed, destination).toJSONString());
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        if (stringParameter == null) {
            stringParameter = new StringParameter("destination", NLS.get("MoveIndexAliasJobFactory.destinationParameter"));
            stringParameter.markRequired();
        }

        if (entityDescriptorParameter == null) {
            entityDescriptorParameter = new ElasticEntityDescriptorParameter("ed", NLS.get("MoveIndexAliasJobFactory.descriptorParameter"));
            entityDescriptorParameter.markRequired();
        }

        parameterCollector.accept(stringParameter);
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
        return "MAINTENANCE";
    }

    @Nonnull
    @Override
    public String getName() {
        return "move-index-alias";
    }
}
