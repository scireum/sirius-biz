/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.mongo;

import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.tenants.mongo.DeleteMongoEntitiesTask;
import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link sirius.biz.jobs.presets.JobPreset job presets} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoJobPresets.FRAMEWORK_PRESETS_MONGO)
public class DeleteMongoJobPresetsTask extends DeleteMongoEntitiesTask {

    @Override
    protected Class<? extends MongoTenantAware> getEntityClass() {
        return MongoJobPreset.class;
    }

    @Override
    public int getPriority() {
        return 110;
    }
}
