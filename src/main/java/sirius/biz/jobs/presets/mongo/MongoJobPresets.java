/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.mongo;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.jobs.JobConfigData;
import sirius.biz.tenants.mongo.MongoTenants;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.List;

/**
 * Provides the implementation of {@link JobPresets} while using a MongoDB database when storing entries.
 */
@Register(framework = MongoJobPresets.FRAMEWORK_PRESETS_MONGO)
public class MongoJobPresets implements JobPresets {

    /**
     * Names the framework which must be enabled to activate the presets for MongoDB based entries.
     */
    public static final String FRAMEWORK_PRESETS_MONGO = "biz.job-presets-mongo";

    @Part
    private Mango mango;

    @Part
    private MongoTenants tenants;

    @Override
    public List<? extends JobPreset> fetchPresets(JobFactory factory) {
        return mango.select(MongoJobPreset.class)
                    .eq(MongoJobPreset.TENANT, tenants.getRequiredTenant())
                    .eq(MongoJobPreset.JOB_CONFIG_DATA.inner(JobConfigData.JOB), factory.getName())
                    .orderAsc(MongoJobPreset.JOB_CONFIG_DATA.inner(JobConfigData.LABEL))
                    .queryList();
    }
}
