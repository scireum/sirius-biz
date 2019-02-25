/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic.reindex;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.ElasticEntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which moves the alias which marks an active index to a desired destination index.
 */
@Register(classes = JobFactory.class)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
public class MoveIndexAliasJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private Mixing mixing;

    private ElasticEntityDescriptorParameter entityDescriptorParameter =
            (ElasticEntityDescriptorParameter) new ElasticEntityDescriptorParameter("ed",
                                                                                    "Entity")
                    .markRequired();
    private StringParameter destinationParameter =
            new StringParameter("destination", "Destination").markRequired();

    @Override
    public String getLabel() {
        return "ES: Move Index Alias";
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return Strings.apply("Moving active elasticsearch alias from index '%s' to '%s'",
                             Strings.join(elastic.getLowLevelClient()
                                                 .getIndicesForAlias(entityDescriptorParameter.require(context)), ","),
                             destinationParameter.require(context));
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        String destination = process.require(destinationParameter);
        EntityDescriptor ed = process.require(entityDescriptorParameter);

        process.log(elastic.getLowLevelClient().moveActiveAlias(ed, destination).toJSONString());
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(destinationParameter);
        parameterCollector.accept(entityDescriptorParameter);
    }

    @Nonnull
    @Override
    public String getName() {
        return "move-index-alias";
    }
}
