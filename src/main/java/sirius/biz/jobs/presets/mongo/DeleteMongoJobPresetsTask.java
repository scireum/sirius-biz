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
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link sirius.biz.jobs.presets.JobPreset job presets} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = MongoJobPresets.FRAMEWORK_PRESETS_MONGO)
public class DeleteMongoJobPresetsTask extends DeleteMongoEntitiesTask<MongoJobPreset> {

    @Override
    protected Class<MongoJobPreset> getEntityClass() {
        return MongoJobPreset.class;
    }

    @Override
    public int getPriority() {
        return 110;
    }
}
