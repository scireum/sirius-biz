/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler;

import sirius.biz.jobs.Jobs;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.ProcessLink;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.StoredObject;
import sirius.db.jdbc.OMA;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Responsible for executing all {@link SchedulerEntry scheduler entries} of all {@link SchedulerEntryProvider providers}.
 */
@Register(classes = {BackgroundLoop.class, JobSchedulerLoop.class})
public class JobSchedulerLoop extends BackgroundLoop {

    @Part
    private Jobs jobs;

    @Part
    private Processes processes;

    @Parts(SchedulerEntryProvider.class)
    private PartCollection<SchedulerEntryProvider<?>> providers;

    private Set<String> changedFiles = new HashSet<>();

    @Part
    private OMA oma;

    @Nonnull
    @Override
    public String getName() {
        return "job-scheduler";
    }

    @Override
    public double maxCallFrequency() {
        return 1d / 50;
    }

    /**
     * Method can be used to add a changed file to background loop execution process.
     *
     * @param fileObjectKey the {@link StoredObject#getObjectKey()} of the file changed
     */
    public synchronized void trackChangedFile(String fileObjectKey) {
        this.changedFiles.add(fileObjectKey);
    }

    private synchronized List<String> getChangedFiles() {
        List<String> list = new ArrayList<>(changedFiles);
        changedFiles.clear();
        return list;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        int startedJobs = 0;
        for (SchedulerEntryProvider<?> provider : providers) {
            startedJobs += executeEntriesOfProvider(now, provider);
            startedJobs += executeEntriesOfChangedFiles(now, provider);
        }

        if (startedJobs == 0) {
            return null;
        }

        return "Started Jobs: " + startedJobs;
    }

    private <J extends SchedulerEntry> int executeEntriesOfChangedFiles(LocalDateTime now,
                                                                        SchedulerEntryProvider<J> provider) {
        List<String> changedFilesToProcess = getChangedFiles();

        if (changedFilesToProcess.isEmpty()) {
            return 0;
        }

        int startedJobs = 0;

        for (J entry : provider.getFileTriggeredJobs()) {
            try {
                if (entry.getUploadTriggerData().shouldRun(changedFilesToProcess)) {
                    executeJob(provider, entry, now);
                    startedJobs++;
                }
            } catch (Exception e) {
                Exceptions.handle()
                        .to(Log.BACKGROUND)
                        .error(e)
                        .withSystemErrorMessage("An error occured while checking a scheduled task of %s: %s - %s (%s)",
                                provider.getClass().getSimpleName(),
                                entry)
                        .handle();
            }
        }

        return startedJobs;
    }

    private <J extends SchedulerEntry> int executeEntriesOfProvider(LocalDateTime now,
                                                                    SchedulerEntryProvider<J> provider) {
        int startedJobs = 0;
        for (J entry : provider.getActiveScheduledJobs()) {
            try {
                if (entry.getSchedulerData().shouldRun(now)) {
                    entry = provider.fetchFullInformation(entry);
                    executeJob(provider, entry, now);
                    startedJobs++;
                }
            } catch (Exception e) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .error(e)
                          .withSystemErrorMessage("An error occured while checking a scheduled task of %s: %s - %s (%s)",
                                                  provider.getClass().getSimpleName(),
                                                  entry)
                          .handle();
            }
        }

        return startedJobs;
    }

    private <J extends SchedulerEntry> void executeJob(SchedulerEntryProvider<J> provider, J entry, LocalDateTime now) {
        try {
            UserInfo user = UserContext.get().getUserManager().findUserByUserId(entry.getSchedulerData().getUserId());
            UserContext.get().runAs(user, () -> executeJobAsUser(provider, entry, now));
        } catch (Exception e) {
            Exceptions.handle()
                    .to(Log.BACKGROUND)
                    .error(e)
                    .withSystemErrorMessage("An error occured while starting a scheduled task of %s: %s - %s (%s)",
                            provider.getClass().getSimpleName(),
                            entry)
                    .handle();
        }
    }

    private <J extends SchedulerEntry> void executeJobAsUser(SchedulerEntryProvider<J> provider,
                                                             J entry,
                                                             LocalDateTime now) {
        processes.executeInStandbyProcessForCurrentTenant("biz-scheduler",
                                                          () -> "Job Scheduler",
                                                          ctx -> executeJobInProcess(provider, entry, now, ctx));
    }

    private <J extends SchedulerEntry> void executeJobInProcess(SchedulerEntryProvider<J> provider,
                                                                J entry,
                                                                LocalDateTime now,
                                                                ProcessContext ctx) {
        if (ctx.isDebugging()) {
            ctx.debug(ProcessLog.info()
                                .withFormattedMessage("Starting scheduled job %s (%s) for user %s.",
                                                      entry,
                                                      entry.getJobConfigData().getJobName(),
                                                      UserContext.getCurrentUser().getUserName()));
        }

        String processId = entry.getJobConfigData()
                                .getJobFactory()
                                .startInBackground(entry.getJobConfigData().asParameterProvider());

        if (processId != null) {
            processes.log(processId,
                          ProcessLog.info()
                                    .withNLSKey("JobSchedulerLoop.scheduledExecutionInfo")
                                    .withContext("entry", entry.toString()));
            processes.addLink(processId,
                              new ProcessLink().withLabel("$JobSchedulerLoop.jobLink")
                                               .withUri("/jobs/scheduler/entry/" + entry.getIdAsString()));
            processes.addReference(processId, entry.getUniqueName());
        }

        provider.markExecuted(entry, now);
    }
}
