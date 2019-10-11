/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.jdbc;

import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.tenants.jdbc.DeleteSQLEntitiesTask;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link sirius.biz.jobs.presets.JobPreset job presets} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = SQLJobPresets.FRAMEWORK_PRESETS_JDBC)
public class DeleteSQLJobPresetsTask extends DeleteSQLEntitiesTask {

    @Override
    protected Class<? extends SQLTenantAware> getEntityClass() {
        return SQLJobPreset.class;
    }

    @Override
    public int getPriority() {
        return 110;
    }
}
