/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.mongo;

import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.scheduler.JobConfigData;
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

    @Override
    public JobConfigData getJobConfigData() {
        return jobConfigData;
    }
}
