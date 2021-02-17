/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.kernel.Sirius;
import sirius.kernel.Startable;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.settings.Extension;
import sirius.kernel.timer.EndOfDayTask;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In charge of updating the configuration and the repository of attached Jupiter instances.
 */
@Register(framework = Jupiter.FRAMEWORK_JUPITER, classes = {JupiterSync.class, Startable.class, EndOfDayTask.class})
public class JupiterSync implements Startable, EndOfDayTask {

    @ConfigValue("jupiter.updateConfig")
    private List<String> updateConfig;

    @ConfigValue("jupiter.automaticUpdate")
    private boolean automaticUpdate;

    @Parts(JupiterConfigUpdater.class)
    private PartCollection<JupiterConfigUpdater> updaters;

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    @Part
    private Jupiter jupiter;

    @Part
    private Processes processes;

    @Part
    private Tasks tasks;

    @Part
    private Elastic elastic;

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    public String getName() {
        return "Jupiter Sync";
    }

    @Override
    public void execute() throws Exception {
        if (automaticUpdate) {
            tasks.defaultExecutor().start(this::performSync);
        }
    }

    @Override
    public void started() {
        if (automaticUpdate) {
            elastic.getReadyFuture().onSuccess(this::performSync);
        }
    }

    protected void performSync() {
        processes.executeInStandbyProcess("jupiter-sync",
                                          () -> "Jupiter Synchronization",
                                          tenants.getSystemTenantId(),
                                          tenants::getSystemTenantName,
                                          this::performSyncInProcess);
    }

    /**
     * Synchronizes all Jupiter instances and reports all activity into the given process.
     *
     * @param processContext the process used for logging and reporting
     */
    public void performSyncInProcess(ProcessContext processContext) {
        try {
            syncConfigs(processContext);
        } catch (Exception e) {
            processContext.handle(e);
        }
    }

    private void syncConfigs(ProcessContext processContext) {
        for (String instance : updateConfig) {
            JupiterConnector connection = jupiter.getConnector(instance);
            if (connection.isConfigured()) {
                try {
                    updateJupiterConfig(processContext, connection);
                } catch (HandledException e) {
                    processContext.log(ProcessLog.error()
                                                 .withFormattedMessage("Failed to update config of %s: %s",
                                                                       instance,
                                                                       e.getMessage()));
                }
            } else {
                processContext.log(ProcessLog.info()
                                             .withFormattedMessage(
                                                     "Not updating Jupiter config of %s as no connection configuration is present!",
                                                     instance));
            }
        }
    }

    private void updateJupiterConfig(ProcessContext processContext, JupiterConnector connection) {
        try {
            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage("Updating config for %s...", connection.getName()));

            Map<String, Object> config = new HashMap<>();
            Extension systemConfig = Sirius.getSettings().getExtension("jupiter.settings", connection.getName());
            for (JupiterConfigUpdater updater : updaters) {
                updater.emitConfig(connection.getName(), systemConfig, config);
            }

            String configString = asYaml(config);
            connection.updateConfig(configString);

            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage("Updated config for %s:%n%s",
                                                                 connection.getName(),
                                                                 configString));
        } catch (Exception e) {
            processContext.handle(Exceptions.handle()
                                            .error(e)
                                            .withSystemErrorMessage("Failed to update Jupiter config of %s: %s (%s)",
                                                                    connection.getName())
                                            .handle());
        }
    }

    private String asYaml(Map<String, Object> config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        return yaml.dump(config);
    }
}
