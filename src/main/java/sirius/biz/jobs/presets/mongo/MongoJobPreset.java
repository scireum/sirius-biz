/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.mongo;

import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.JobConfigData;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.di.std.Framework;

/**
 * Provices the entity to store a {@link JobPreset} in a MongoDB database.
 */
@TranslationSource(JobPreset.class)
@Framework(MongoJobPresets.FRAMEWORK_PRESETS_MONGO)
public class MongoJobPreset extends MongoTenantAware implements JobPreset {

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
