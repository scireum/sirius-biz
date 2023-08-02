/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.s3.BucketName;
import sirius.biz.storage.s3.ObjectStore;
import sirius.biz.storage.s3.ObjectStores;
import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.kernel.Sirius;
import sirius.kernel.Startable;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
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
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * In charge of updating the configuration and the repository of attached Jupiter instances.
 * <p>
 * Next to iterating over all Jupiter instances and computing their config via {@link JupiterConfigUpdater}, this also
 * synchronizes the contents of an object store (e.g. Amazon S3) as well as a local repository against the repository
 * stored in Jupiter.
 * <p>
 * Note that this also schedules all {@link JupiterDataProvider} so that they can store or update their data in
 * the local repository (which will then be directly uploaded into Jupiter).
 */
@Register(framework = Jupiter.FRAMEWORK_JUPITER, classes = {JupiterSync.class, Startable.class, EndOfDayTask.class})
public class JupiterSync implements Startable, EndOfDayTask {

    /**
     * Specifies the number of attempts when waiting for the repository to be in "epoch sync".
     * <p>
     * After the repository contents have been synced (or more exactly, their sync has been requested) we ask jupiter
     * to increment the epochs (basically a simple counter) for the frontend and backend actors. As the frontend
     * actor will immediatelly incement its value but the background actor will put this task into its internal queue,
     * we know that once the values are the same again, all background tasks which were previously scheduled, are
     * completed.
     * <p>
     * This specifies the number of attempts (query the values, compare them, and wait for
     * {@link #SYNC_AWAIT_PAUSE_SECONDS} in case the values do not match).
     */
    private static final int MAX_ATTEMPTS_FOR_EPOCH_SYNC = 30;

    /**
     * Specifies the wait interval when awaiting synchronized epochs.
     *
     * @see #MAX_ATTEMPTS_FOR_EPOCH_SYNC
     */
    private static final int SYNC_AWAIT_PAUSE_SECONDS = 2;

    @ConfigValue("jupiter.updateConfig")
    private List<String> updateConfig;

    @ConfigValue("jupiter.automaticUpdate")
    private boolean automaticUpdate;

    @ConfigValue("jupiter.syncRepository")
    private List<String> syncRepository;

    @ConfigValue("jupiter.repository.localSpaceName")
    private String localRepoSpaceName;

    @ConfigValue("jupiter.repository.hostUrl")
    private String jupiterHostUrl;

    @Part
    private ObjectStores objectStores;

    @Part
    @Nullable
    private BlobStorage blobStorage;

    @Parts(JupiterDataProvider.class)
    private PartCollection<JupiterDataProvider> dataProviders;

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
        return "sync-jupiter";
    }

    @Override
    public void execute() throws Exception {
        runInStandbyProcess(processContext -> performSyncInProcess(processContext, true, true, true));
    }

    @Override
    public void started() {
        // As we run in the "readiness callback" of elastic, we rather fork a thread here, as synchronizing the
        // repository might take a while.
        // We await the readiness of elastic in the first place, as we use the Process framework to log what we
        // do...
        elastic.getReadyFuture()
               .onSuccess(() -> tasks.defaultExecutor()
                                     .start(() -> runInStandbyProcess(processContext -> performSyncInProcess(
                                             processContext,
                                             true,
                                             true,
                                             false))));
    }

    protected void runInStandbyProcess(Consumer<ProcessContext> processContextConsumer) {
        if (automaticUpdate) {
            processes.executeInStandbyProcess("jupiter-sync",
                                              () -> "Jupiter Synchronization",
                                              tenants.getSystemTenantId(),
                                              tenants::getSystemTenantName,
                                              processContextConsumer);
        }
    }

    /**
     * Synchronizes all Jupiter instances and reports all activity into the given process.
     *
     * @param processContext    the process used for logging and reporting
     * @param syncConfig        determines if the config should be synced
     * @param syncRepo          determines if the repository should be synced
     * @param syncDataProviders determines if the {@link JupiterDataProvider data providers} should be invoked so that
     *                          the local repository is filled
     */
    public void performSyncInProcess(ProcessContext processContext,
                                     boolean syncConfig,
                                     boolean syncRepo,
                                     boolean syncDataProviders) {
        try {
            if (syncDataProviders) {
                executeDataProviders(processContext);
            }
            if (syncConfig) {
                syncConfigs(processContext);
            }
            if (syncRepo) {
                syncRepositories(processContext);
            }

            processContext.log(ProcessLog.info().withMessage("Flushing local cache..."));
            jupiter.flushCaches();
        } catch (Exception e) {
            processContext.handle(e);
        }
    }

    private void executeDataProviders(ProcessContext processContext) {
        if (Strings.isEmpty(localRepoSpaceName) || blobStorage == null) {
            return;
        }

        for (JupiterDataProvider provider : dataProviders) {
            executeDataProvider(processContext, provider);
        }
    }

    private void executeDataProvider(ProcessContext processContext, JupiterDataProvider provider) {
        Watch watch = Watch.start();
        processContext.log(ProcessLog.info().withFormattedMessage("Executing data provider: %s", provider.getName()));

        Blob blob = blobStorage.getSpace(localRepoSpaceName)
                               .findOrCreateByPath(tenants.getSystemTenantId(),
                                                   provider.getFilename());

        try (OutputStream out = blob.createOutputStream(Files.getFilenameAndExtension(provider.getFilename()))) {
            provider.execute(out);
            processContext.log(ProcessLog.info()
                                         .withFormattedMessage("Creating '%s' took %s...",
                                                               provider.getFilename(),
                                                               watch.duration()));
        } catch (Exception e) {
            processContext.log(ProcessLog.error()
                                         .withMessage(Exceptions.handle()
                                                                .to(Jupiter.LOG)
                                                                .error(e)
                                                                .withSystemErrorMessage(
                                                                        "Failed to execute data provider %s: %s (%s)",
                                                                        provider.getName())
                                                                .handle()
                                                                .getMessage()));
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
                updater.emitConfig(connection, systemConfig, config);
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

    private void syncRepositories(ProcessContext processContext) {
        for (String instance : syncRepository) {
            JupiterConnector connection = jupiter.getConnector(instance);
            if (connection.isConfigured()) {
                try {
                    syncRepository(processContext, connection);
                    awaitNextEpoch(processContext, connection);
                } catch (HandledException e) {
                    processContext.log(ProcessLog.error()
                                                 .withFormattedMessage("Failed to sync repository contents of %s: %s",
                                                                       instance,
                                                                       e.getMessage()));
                    Jupiter.LOG.SEVERE(e);
                }
            } else {
                processContext.log(ProcessLog.info()
                                             .withFormattedMessage(
                                                     "Not syncing repository contents of %s as not connection configuration is present!",
                                                     instance));
            }
        }
    }

    private void syncRepository(ProcessContext processContext, JupiterConnector connection) {
        try {
            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage("Synchronizing repository contents of %s...",
                                                                 connection.getName()));
            List<RepositoryFile> repositoryFiles = connection.repository().list();

            Set<String> filesToDelete =
                    repositoryFiles.stream().map(RepositoryFile::getName).collect(Collectors.toSet());

            // We need to buffer our update tasks, as we want to delete old files, before loading new ones, so that
            // moves of loaders etc. work properly...
            List<Runnable> updateTasks = new ArrayList<>();

            syncLocalRepository(processContext, connection, repositoryFiles, updateTasks::add, filesToDelete);
            syncUplinkRepositories(processContext, connection, repositoryFiles, updateTasks::add, filesToDelete);

            for (String file : filesToDelete) {
                processContext.log(ProcessLog.info()
                                             .withFormattedMessage("Deleting %s for %s", file, connection.getName()));
                connection.repository().delete(file);
            }

            // Execute update tasks after deletes so that a loader being moved doesn't delete itself after its update...
            executeUpdates(processContext, updateTasks);

            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage(
                                                   "Successfully synchronized the repository contents of %s.",
                                                   connection.getName()));
        } catch (Exception e) {
            processContext.handle(Exceptions.handle()
                                            .error(e)
                                            .withSystemErrorMessage(
                                                    "Failed to synchronize the repository contents of %s: %s (%s)",
                                                    connection.getName())
                                            .handle());
        }
    }

    private void executeUpdates(ProcessContext processContext, List<Runnable> updateTasks) {
        for (Runnable runnable : updateTasks) {
            try {
                runnable.run();
            } catch (Exception ex) {
                processContext.handle(ex);
            }
        }
    }

    private void syncUplinkRepositories(ProcessContext processContext,
                                        JupiterConnector connection,
                                        List<RepositoryFile> repositoryFiles,
                                        Consumer<Runnable> updateTaskConsumer,
                                        Set<String> filesToDelete) {
        Set<String> enabledNamespaces = new HashSet<>(connection.fetchEnabledNamespaces());
        for (Extension uplinkStore : Sirius.getSettings().getExtensions("jupiter.repository.uplinks")) {
            String store = uplinkStore.get("store").asString();
            String bucket = getEffectiveUplinkBucket(uplinkStore);
            List<String> requiredNamespace = uplinkStore.getStringList("requiredNamespaces");
            if (!requiredNamespace.isEmpty() && requiredNamespace.stream().noneMatch(enabledNamespaces::contains)) {
                processContext.debug(ProcessLog.info()
                                               .withFormattedMessage(
                                                       "Skipping uplink checks for %s / %s as none of the required namespaces is enabled.",
                                                       uplinkStore.getId(),
                                                       connection.getName()));
            } else if (Strings.isEmpty(store) || Strings.isEmpty(bucket) || !objectStores.isConfigured(store)) {
                processContext.debug(ProcessLog.info()
                                               .withFormattedMessage(
                                                       "Skipping uplink checks for %s / %s as no repository or bucket is configured.",
                                                       uplinkStore.getId(),
                                                       connection.getName()));
            } else {
                syncUplinkRepository(processContext,
                                     connection,
                                     repositoryFiles,
                                     updateTaskConsumer,
                                     filesToDelete,
                                     uplinkStore);
            }
        }
    }

    private void syncUplinkRepository(ProcessContext processContext,
                                      JupiterConnector connection,
                                      List<RepositoryFile> repositoryFiles,
                                      Consumer<Runnable> updateTaskConsumer,
                                      Set<String> filesToDelete,
                                      Extension uplinkStore) {
        String store = uplinkStore.get("store").asString();
        String bucket = getEffectiveUplinkBucket(uplinkStore);

        processContext.debug(ProcessLog.info()
                                       .withFormattedMessage("Checking uplink repository %s (%s in %s) for %s...",
                                                             uplinkStore.getId(),
                                                             bucket,
                                                             store,
                                                             connection.getName()));

        ObjectStore objectStore = objectStores.getStore(store);
        BucketName uplinkBucketName = objectStore.getBucketName(bucket);
        objectStore.listObjects(uplinkBucketName, null, object -> {
            if (object.getSize() > 0 && uplinkStore.getStringList("ignoredPaths")
                                                   .stream()
                                                   .noneMatch(ignoredPath -> object.getKey().startsWith(ignoredPath))) {
                handleUplinkFile(processContext,
                                 connection,
                                 repositoryFiles,
                                 updateTaskConsumer,
                                 filesToDelete,
                                 objectStore,
                                 uplinkBucketName,
                                 object);
            }

            return true;
        });
    }

    private String getEffectiveUplinkBucket(Extension uplinkStore) {
        if (!Sirius.isProd() && Strings.isFilled(uplinkStore.getString("testBucket"))) {
            return uplinkStore.getString("testBucket");
        }

        return uplinkStore.getString("bucket");
    }

    @SuppressWarnings("java:S107")
    @Explain("In this case using 8 parameters is the simplest way to extract this block of logic.")
    private void handleUplinkFile(ProcessContext processContext,
                                  JupiterConnector connection,
                                  List<RepositoryFile> repositoryFiles,
                                  Consumer<Runnable> updateTaskConsumer,
                                  Set<String> filesToDelete,
                                  ObjectStore store,
                                  BucketName uplinkBucketName,
                                  S3ObjectSummary object) {
        String effectiveFileName = "/" + object.getKey();
        RepositoryFile repositoryFile = repositoryFiles.stream()
                                                       .filter(file -> Strings.areEqual(file.getName(),
                                                                                        effectiveFileName))
                                                       .findFirst()
                                                       .orElse(null);

        if (repositoryFile == null || repositoryFile.getLastModified()
                                                    .isBefore(object.getLastModified()
                                                                    .toInstant()
                                                                    .atZone(ZoneId.systemDefault())
                                                                    .toLocalDateTime())) {
            String url = store.objectUrl(uplinkBucketName, object.getKey());
            updateTaskConsumer.accept(() -> {
                processContext.log(ProcessLog.info()
                                             .withFormattedMessage("Fetching %s for %s as it is new or updated...",
                                                                   effectiveFileName,
                                                                   connection.getName()));
                connection.repository().fetchUrl(effectiveFileName, url, false);
            });
        } else {
            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage("Skipping %s for %s as it is unchanged...",
                                                                 effectiveFileName,
                                                                 connection.getName()));
        }

        filesToDelete.remove(effectiveFileName);
    }

    private void syncLocalRepository(ProcessContext processContext,
                                     JupiterConnector connection,
                                     List<RepositoryFile> repositoryFiles,
                                     Consumer<Runnable> updateTaskConsumer,
                                     Set<String> filesToDelete) {
        if (blobStorage == null || tenants == null || Strings.isEmpty(localRepoSpaceName)) {
            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage(
                                                   "Skipping local repository for %s as no storage space is configured.",
                                                   connection.getName()));
            return;
        }

        processContext.debug(ProcessLog.info()
                                       .withFormattedMessage("Checking local repository in storage space %s for %s...",
                                                             localRepoSpaceName,
                                                             connection.getName()));

        try {
            Directory root = blobStorage.getSpace(localRepoSpaceName)
                                        .getRoot(tenants.getSystemTenantId());
            visitLocalDirectory(processContext,
                                null,
                                root,
                                connection,
                                repositoryFiles,
                                updateTaskConsumer,
                                filesToDelete);
        } catch (Exception e) {
            processContext.handle(Exceptions.handle()
                                            .error(e)
                                            .withSystemErrorMessage(
                                                    "Failed to check the local repository %s for %s: %s (%s)",
                                                    localRepoSpaceName,
                                                    connection.getName())
                                            .handle());
        }
    }

    private void visitLocalDirectory(ProcessContext processContext,
                                     @Nullable String prefix,
                                     Directory currentDirectory,
                                     JupiterConnector connection,
                                     List<RepositoryFile> repositoryFiles,
                                     Consumer<Runnable> updateTaskConsumer,
                                     Set<String> filesToDelete) {
        String effectivePrefix = Value.of(prefix).asString();

        currentDirectory.listChildBlobs(null, null, 0, child -> {
            handleLocalFile(processContext,
                            connection,
                            repositoryFiles,
                            updateTaskConsumer,
                            filesToDelete,
                            effectivePrefix + "/" + child.getFilename(),
                            child);
            return true;
        });
        currentDirectory.listChildDirectories(null, 0, directory -> {
            visitLocalDirectory(processContext,
                                effectivePrefix + "/" + directory.getName(),
                                directory,
                                connection,
                                repositoryFiles,
                                updateTaskConsumer,
                                filesToDelete);
            return true;
        });
    }

    private void handleLocalFile(ProcessContext processContext,
                                 JupiterConnector connection,
                                 List<RepositoryFile> repositoryFiles,
                                 Consumer<Runnable> updateTaskConsumer,
                                 Set<String> filesToDelete,
                                 String effectiveFileName,
                                 Blob child) {
        RepositoryFile repositoryFile = repositoryFiles.stream()
                                                       .filter(file -> Strings.areEqual(file.getName(),
                                                                                        effectiveFileName))
                                                       .findFirst()
                                                       .orElse(null);

        if (repositoryFile == null || repositoryFile.getLastModified().isBefore(child.getLastModified())) {
            String url = child.url()
                              .withBaseURL(jupiterHostUrl)
                              .asDownload()
                              .buildURL()
                              .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                      "Unable to build blob download url for: %s (%s)",
                                      effectiveFileName,
                                      child.getBlobKey())));
            updateTaskConsumer.accept(() -> {
                processContext.log(ProcessLog.info()
                                             .withFormattedMessage("Fetching %s for %s as it is new or updated...",
                                                                   effectiveFileName,
                                                                   connection.getName()));
                connection.repository().fetchUrl(effectiveFileName, url, false);
            });
        } else {
            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage("Skipping %s for %s as it is unchanged...",
                                                                 effectiveFileName,
                                                                 connection.getName()));
        }

        filesToDelete.remove(effectiveFileName);
    }

    private void awaitNextEpoch(ProcessContext processContext, JupiterConnector connector) {
        connector.repository().requestEpoch();
        int attempts = MAX_ATTEMPTS_FOR_EPOCH_SYNC;
        Monoflop stateUpdate = Monoflop.create();
        while (processContext.isActive() && attempts-- > 0 && !connector.repository().isEpochInSync()) {
            if (stateUpdate.firstCall()) {
                processContext.forceUpdateState(Strings.apply("Waiting for the repository of %s to be synced...",
                                                              connector.getName()));
            }
            Wait.seconds(SYNC_AWAIT_PAUSE_SECONDS);
        }

        if (stateUpdate.successiveCall()) {
            processContext.forceUpdateState(null);
        }

        if (connector.repository().isEpochInSync()) {
            processContext.log(ProcessLog.info()
                                         .withFormattedMessage("Repository of %s is fully synced...",
                                                               connector.getName()));
        } else {
            processContext.log(ProcessLog.warn()
                                         .withFormattedMessage(
                                                 "Repository of %s was unable to synchronize within 60 seconds...",
                                                 connector.getName()));
        }
    }

    /**
     * Stores a file in the local repository which will be synchronized/uploaded into the attached Jupiter instances.
     *
     * @param path the destination path to store the file at
     * @return an output stream which can be supplied with the data to transfer to Jupiter
     */
    public OutputStream storeLocalRepositoryFile(String path) {
        if (Strings.isEmpty(localRepoSpaceName) || blobStorage == null) {
            throw new IllegalStateException("No local repository is configured.");
        }
        Blob blob = blobStorage.getSpace(localRepoSpaceName)
                               .findOrCreateByPath(tenants.getSystemTenantId(), path);
        return blob.createOutputStream(() -> {
            runInStandbyProcess(processContext -> performSyncInProcess(processContext, false, false, true));
        }, Files.getFilenameAndExtension(path));
    }
}
