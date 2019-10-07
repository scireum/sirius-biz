/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.jdbc;

import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.scheduler.JobConfigData;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.di.std.Framework;

/**
 * Provices the entity to store a {@link JobPreset} in a JDBC database.
 */
@TranslationSource(JobPreset.class)
@Framework(SQLJobPresets.FRAMEWORK_PRESETS_JDBC)
public class SQLJobPreset extends SQLTenantAware implements JobPreset {

    private final JobConfigData jobConfigData = new JobConfigData();

    @Override
    public JobConfigData getJobConfigData() {
        return jobConfigData;
    }
}
