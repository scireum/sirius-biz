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
import sirius.biz.jobs.params.EntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.db.es.IndexMappings;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which moves the alias which marks an active index to a desired destination index.
 */
@Register(classes = JobFactory.class)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class MoveIndexAliasJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private Mixing mixing;

    @Part
    private IndexMappings mappings;

    private EntityDescriptorParameter entityDescriptorParameter =
            new EntityDescriptorParameter("ed", "Entity").withFilter(EntityDescriptorParameter::isElasticEntity)
                                                         .markRequired();
    private StringParameter destinationParameter = new StringParameter("destination", "Destination").markRequired();

    @Override
    public String getLabel() {
        return "Move Elasticsearch Index Alias";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Moves the alias of the given entity to the given target index.";
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
        elastic.getLowLevelClient().getIndicesForAlias(ed).forEach(index -> {
            process.log(Strings.apply("Setting dynamic mapping mode to 'strict' for index '%s'.", index));
            // set the dynamic mapping mode to 'strict' as most probably it is currently set to 'false' which was used
            // during reindexing
            mappings.createMapping(ed, index, IndexMappings.DynamicMapping.STRICT);
        });
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
