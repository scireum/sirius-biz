/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import com.fasterxml.jackson.databind.JsonNode;
import sirius.biz.cluster.InterconnectClusterManager;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Json;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.kernel.nls.Translation;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Collects all unused NLS keys across all cluster nodes and reports the intersection as text file.
 * <p>
 * We build the intersection here to determine the truly unused keys, as maybe some nodes have specific tasks
 * and will not use all keys.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ReportUnusedNLSKeysJob extends SimpleBatchProcessJobFactory {

    @Part
    private InterconnectClusterManager clusterManager;

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // This job doesn't require any parameters.
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel();
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Override
    public String getLabel() {
        return "Export all unused NLS keys";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Queries all unused NLS keys on each node in the cluster, computes the intersection and reports this as textfile within the created process.";
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        process.log("Fetching unused keys from all nodes...");

        // Fill keys with locally missing keys (this is also required, if no cluster nodes at all are present
        Set<String> missingKeys = new HashSet<>();
        NLS.getTranslationEngine().getUnusedTranslations().map(Translation::getKey).forEach(missingKeys::add);

        clusterManager.callEachNode("/system/nls/unused/" + clusterManager.getClusterAPIToken())
                      .forEach(missingKeysOfNode -> {
                          Set<String> keysOfNode = Json.getArray(missingKeysOfNode, "unused")
                                                       .valueStream()
                                                       .map(JsonNode::asText)
                                                       .collect(Collectors.toSet());

                          process.log(ProcessLog.info()
                                                .withFormattedMessage("Received %s keys from %s",
                                                                      keysOfNode.size(),
                                                                      missingKeysOfNode.path(InterconnectClusterManager.RESPONSE_NODE_NAME)
                                                                                       .asText()));
                          missingKeys.retainAll(keysOfNode);
                      });

        if (missingKeys.isEmpty()) {
            process.log("No unused keys found...");
        } else {
            process.log(ProcessLog.info().withFormattedMessage("Exporting %s unused keys...", missingKeys.size()));
            try (PrintStream out = new PrintStream(process.addFile("unused_keys.txt"))) {
                missingKeys.forEach(out::println);
            }
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "report-unused-nls-keys";
    }

    @Override
    public int getPriority() {
        return 8500;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }
}
