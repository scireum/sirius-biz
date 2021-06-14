/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

import java.util.HashMap;
import java.util.Map;

/**
 * Forwards the "namespaces" setting from the local config into the Jupiter instance configs.
 * <p>
 * The "namespaces" control which of the files in the repository are actually loaded by Jupiter. This is used, as
 * we commonly use a single shared repository but depending on the application and the needs of each installation
 * not all data is required.
 */
@Register
public class RepositoryConfigUpdater implements JupiterConfigUpdater {

    @Override
    public void emitConfig(String instance, Extension systemConfig, Map<String, Object> config) {
        Map<String, Object> repoConfig = new HashMap<>();
        repoConfig.put("namespaces", systemConfig.getStringList("repository.namespaces"));
        config.put("repository", repoConfig);
    }
}
