/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.jdbc;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.jobs.scheduler.JobConfigData;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.List;

/**
 * Provides the implementation of {@link JobPresets} while using a JDBC database when storing entries.
 */
@Register(framework = SQLJobPresets.FRAMEWORK_PRESETS_JDBC)
public class SQLJobPresets implements JobPresets {

    /**
     * Names the framework which must be enabled to activate the presets for SQL/JDBC based entries.
     */
    public static final String FRAMEWORK_PRESETS_JDBC = "biz.job-presets-jdbc";

    @Part
    private OMA oma;

    @Part
    private SQLTenants tenants;

    @Override
    public List<? extends JobPreset> fetchPresets(JobFactory factory) {
        return oma.select(SQLJobPreset.class)
                  .eq(SQLJobPreset.TENANT, tenants.getRequiredTenant())
                  .eq(SQLJobPreset.JOB_CONFIG_DATA.inner(JobConfigData.JOB), factory.getName())
                  .orderAsc(SQLJobPreset.JOB_CONFIG_DATA.inner(JobConfigData.LABEL))
                  .queryList();
    }
}
