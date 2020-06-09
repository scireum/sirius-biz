/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets.jdbc;

import sirius.biz.jobs.presets.JobPresetsController;
import sirius.kernel.di.std.Register;

/**
 * Provides the actual controller responsible for managing job presets based in a JDBC database as storage.
 */
@Register(framework = SQLJobPresets.FRAMEWORK_PRESETS_JDBC)
public class SQLJobPresetsController extends JobPresetsController<SQLJobPreset> {

    @Override
    protected Class<SQLJobPreset> getPresetType() {
        return SQLJobPreset.class;
    }
}
