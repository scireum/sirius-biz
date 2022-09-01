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
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.db.es.IndexMappings;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which moves the alias which marks an active index to a desired destination index.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class MoveIndexAliasJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private Mixing mixing;

    @Part
    private IndexMappings mappings;

    private final Parameter<EntityDescriptor> entityDescriptorParameter =
            new EntityDescriptorParameter().withFilter(EntityDescriptorParameter::isElasticEntity)
                                           .markRequired()
                                           .build();
    private final Parameter<String> destinationParameter =
            new StringParameter("destination", "Destination").markRequired().build();

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
        try {
            return Strings.apply("Moving active Elasticsearch alias from index '%s' to '%s'",
                                 elastic.determineEffectiveIndex(entityDescriptorParameter.require(context)),
                                 destinationParameter.require(context));
        } catch (HandledException e) {
            // In some rare cases, this might fail (if the system is inconsistent anyway). In this case
            // we prefer that the job fails rather than the setup / start crashes...
            Exceptions.ignore(e);
            return Strings.apply("Moving active Elasticsearch alias to '%s'", destinationParameter.require(context));
        }
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        String destination = process.require(destinationParameter);
        EntityDescriptor ed = process.require(entityDescriptorParameter);

        process.log(elastic.getLowLevelClient()
                           .createOrMoveAlias(elastic.determineReadAlias(ed), destination)
                           .toJSONString());

        String effectiveIndex = elastic.determineEffectiveIndex(ed);
        process.log(Strings.apply("Setting dynamic mapping mode to 'strict' for index '%s'.", effectiveIndex));
        // Set the dynamic mapping mode to 'strict' as most probably it is currently set to 'false' which was used
        // during reindexing (see ReindexJobFactory)
        mappings.createMapping(ed, effectiveIndex, IndexMappings.DynamicMapping.STRICT);
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(entityDescriptorParameter);
        parameterCollector.accept(destinationParameter);
    }

    @Nonnull
    @Override
    public String getName() {
        return "move-index-alias";
    }

    @Override
    public String getCategory() {
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }
}
