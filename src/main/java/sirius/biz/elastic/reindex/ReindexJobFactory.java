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
import sirius.biz.process.PersistencePeriod;
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
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a job which reindexes a given index in elastic.
 */
@Register(classes = JobFactory.class)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ReindexJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Elastic elastic;

    @Part
    private IndexMappings mappings;

    private EntityDescriptorParameter entityDescriptorParameter =
            new EntityDescriptorParameter("ed", "Entity").withFilter(EntityDescriptorParameter::isElasticEntity)
                                                         .markRequired();

    @Override
    public String getLabel() {
        return "Re-Index Elasticseach Entity";
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
        String nextIndex = determineNextIndexName(ed);
        // set the dynamic mapping mode to "false", so that legacy fields in documents are just ignored and don't
        // cause the reindex process to abort
        mappings.createMapping(ed, nextIndex, IndexMappings.DynamicMapping.FALSE);
        process.log("Created index: " + nextIndex);

        elastic.getLowLevelClient().reindex(ed, nextIndex, response -> {
        }, exception -> {
        });
        process.log("Started a reindex job in elasticsearch, check the task API to see the progress ...");
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

    @Nonnull
    @Override
    public String getName() {
        return "reindex-index-mapping";
    }
}
