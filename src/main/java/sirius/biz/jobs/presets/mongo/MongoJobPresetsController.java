/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.mongo;

import sirius.biz.jobs.presets.JobPresetsController;
import sirius.kernel.di.std.Register;

/**
 * Provides the actual controller responsible for managing job presets based in a MongoDB database as storage.
 */
@Register(framework = MongoJobPresets.FRAMEWORK_PRESETS_MONGO)
public class MongoJobPresetsController extends JobPresetsController<MongoJobPreset> {

    @Override
    protected Class<MongoJobPreset> getPresetType() {
        return MongoJobPreset.class;
    }
}
