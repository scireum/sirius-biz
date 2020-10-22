/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.jobs.JobCategory;
import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.mongo.PrefixSearchableEntity;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.protocol.Traced;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Executes the <tt>BeforeSave</tt> handlers and <tt>OnValidate</tt> handlers of all entities of the selected type.
 * <p>
 * This can be used to trigger all BeforeSaveHandlers which sometimes need to be re-run in order to initialize an
 * entity or a new field properly. Also it will ensure that all entities are still "re-save" able and also pass
 * all validation warnings.
 *
 * @see sirius.db.mixing.annotations.BeforeSave
 * @see sirius.db.mixing.annotations.OnValidate
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ResaveEntitiesJobFactory extends DefaultBatchProcessFactory {

    private EntityDescriptorParameter descriptorParameter = new EntityDescriptorParameter().markRequired();
    private BooleanParameter executeSaveParameter;
    private BooleanParameter performValidationParameter;

    /**
     * Creates a new instance of the job factory.
     */
    public ResaveEntitiesJobFactory() {
        this.executeSaveParameter = new BooleanParameter("executeSave", "Execute Save");
        this.executeSaveParameter.withDefaultTrue();
        this.executeSaveParameter.withDescription(
                "Determines if the update method of the underlying mapper should be invoked so that all BeforeSave handlers are executed.");

        this.performValidationParameter = new BooleanParameter("performValidation", "Perform Validation");
        this.performValidationParameter.withDefaultTrue();
        this.performValidationParameter.withDescription("Determines if all OnValidate handlers should be invoked.");
    }

    @Override
    protected BatchJob createJob(ProcessContext process) throws Exception {
        return new ResaveEntitiesJob(process);
    }

    private class ResaveEntitiesJob extends BatchJob {
        private final EntityDescriptor descriptor;
        private final boolean executeSave;
        private final boolean performValidation;

        ResaveEntitiesJob(ProcessContext process) {
            super(process);
            this.descriptor = process.require(descriptorParameter);
            this.executeSave = process.require(executeSaveParameter);
            this.performValidation = process.require(performValidationParameter);
        }

        @Override
        public void execute() throws Exception {
            Query<?, ?, ?> query = descriptor.getMapper().select((descriptor.getType().asSubclass(BaseEntity.class)));
            if (query instanceof SmartQuery) {
                ((SmartQuery<? extends SQLEntity>) query).iterateBlockwiseAll(this::processEntity);
            } else {
                query.iterateAll(this::processEntity);
            }
        }

        private void processEntity(BaseEntity<?> entity) {
            Watch watch = Watch.start();
            try {
                if (executeSave) {
                    // We do not want to update the tracing infos here as we most probably don't change anything.
                    //
                    // We do not suppress the Journal (if there is any) because we do not
                    // expect any journalled changes. If there still are any, we have to log them
                    // for traceability reasons.
                    if (entity instanceof Traced) {
                        ((Traced) entity).getTrace().setSilent(true);
                    }

                    if (entity instanceof PrefixSearchableEntity) {
                        ((PrefixSearchableEntity) entity).forceUpdateOfSearchPrefixes();
                    }

                    descriptor.getMapper().update(entity);
                }
                if (performValidation) {
                    List<String> validationgErrors = descriptor.getMapper().validate(entity);
                    if (!validationgErrors.isEmpty()) {
                        process.log(ProcessLog.warn()
                                              .withFormattedMessage("Entity %s with id: %s has validation warnings:\n%s",
                                                                    entity.toString(),
                                                                    entity.getIdAsString(),
                                                                    String.join("\n", validationgErrors)));

                        process.addTiming("Validation-Error", watch.elapsedMillis());
                    }
                }
                process.addTiming("Success", watch.elapsedMillis());
            } catch (Exception e) {
                process.addTiming("Failure", watch.elapsedMillis());
                process.log(ProcessLog.error()
                                      .withFormattedMessage("Failed to save entity %s with id: %s - %s",
                                                            entity.toString(),
                                                            entity.getIdAsString(),
                                                            e.getMessage()));
            }
        }
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return Strings.apply("Re-Save Entities of type: %s",
                             descriptorParameter.get(context)
                                                .map(EntityDescriptor::getType)
                                                .map(Class::getSimpleName)
                                                .orElse("?"));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(descriptorParameter);
        parameterCollector.accept(executeSaveParameter);
        parameterCollector.accept(performValidationParameter);
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_MISC;
    }

    @Override
    public String getLabel() {
        return "Re-Save Entities";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Executes the BeforeSave handlers and OnValidate handlers of all entities of the selected type.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "resave-entities";
    }
}
