/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Reports all stored custom configs which cannot be parsed.
 * <p>
 * Such configs break the scope, tenant or user they belong to at runtime. This job is especially useful after
 * updating the HOCON parser, as newer versions might reject configs which have been accepted before.
 *
 * @see CustomConfigs
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class CheckCustomConfigsJob extends SimpleBatchProcessJobFactory {

    private static final String MESSAGE_TYPE_INVALID_CONFIG = "Invalid config";

    @Part
    private Mixing mixing;

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // This job doesn't require any parameters.
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel();
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Override
    public String getLabel() {
        return "Check stored custom configs";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Parses the stored custom configs of all entities and reports those which are invalid.";
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        AtomicLong checkedConfigs = new AtomicLong();
        AtomicLong invalidConfigs = new AtomicLong();

        for (EntityDescriptor descriptor : mixing.getDescriptors()) {
            CustomConfigs.findConfigProperties(descriptor).forEach(property -> {
                checkedConfigs.addAndGet(checkProperty(process, descriptor, property, invalidConfigs));
            });
        }

        process.log(ProcessLog.info()
                              .withFormattedMessage("Checked %s configs, %s of them are invalid.",
                                                    checkedConfigs.get(),
                                                    invalidConfigs.get()));
    }

    private long checkProperty(ProcessContext process,
                               EntityDescriptor descriptor,
                               Property property,
                               AtomicLong invalidConfigs) {
        try {
            return CustomConfigs.checkStoredConfigs(descriptor, property, invalidConfig -> {
                invalidConfigs.incrementAndGet();
                process.log(ProcessLog.warn()
                                      .withMessageType(MESSAGE_TYPE_INVALID_CONFIG)
                                      .withFormattedMessage("%s (%s), %s: %s",
                                                            invalidConfig.entity().getUniqueName(),
                                                            invalidConfig.entity(),
                                                            invalidConfig.property().getName(),
                                                            invalidConfig.error()));
            });
        } catch (Exception exception) {
            process.handle(exception);
            return 0;
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "check-custom-configs";
    }

    @Override
    public int getPriority() {
        return 8600;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }
}
