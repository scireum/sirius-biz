/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.jdbc;

import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.JobConfigData;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.di.std.Framework;

/**
 * Provides the entity to store a {@link JobPreset} in a JDBC database.
 */
@TranslationSource(JobPreset.class)
@Framework(SQLJobPresets.FRAMEWORK_PRESETS_JDBC)
public class SQLJobPreset extends SQLTenantAware implements JobPreset {

    private final JobConfigData jobConfigData = new JobConfigData();
    private final JournalData journal = new JournalData(this);

    private boolean locked;

    @Override
    public JobConfigData getJobConfigData() {
        return jobConfigData;
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
