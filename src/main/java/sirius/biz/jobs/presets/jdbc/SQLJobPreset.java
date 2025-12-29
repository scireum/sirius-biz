/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.jdbc;

import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.JobConfigData;
import sirius.biz.protocol.JournalData;
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
    private final JournalData journal = new JournalData(this);

    @Override
    public JobConfigData getJobConfigData() {
        return jobConfigData;
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }
}
