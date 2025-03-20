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
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.time.LocalDateTime;

/**
 * Provides the logic to execute a scheduled entry with the appropriate settings.
 */
@Register(framework = Jobs.FRAMEWORK_JOBS, classes = ScheduledEntryExecution.class)
public class ScheduledEntryExecution {

    @Part
    private Processes processes;

    /**
     * Executes the provided scheduled entry immediately using the given provider.
     *
     * @param provider The provider which manages data persistence for the given entry
     * @param entry    the entry to execute
     * @param now      the current time to be used for marking the job execution
     * @param <J>      the type of the entry to execute
     */
    public <J extends SchedulerEntry> void executeJob(SchedulerEntryProvider<J> provider, J entry, LocalDateTime now) {
        try {
            UserInfo user = UserContext.get().getUserManager().findUserByUserId(entry.getSchedulerData().getUserId());
            UserContext.get().runAs(user, () -> executeJobAsUser(provider, entry, now));
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(exception)
                      .withSystemErrorMessage("An error occurred while starting a scheduled task of %s: %s - %s (%s)",
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

        try {
            String processId = entry.getJobConfigData()
                                    .getJobFactory()
                                    .startInBackground(entry.getJobConfigData()::fetchParameter);

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
        } catch (HandledException exception) {
            ctx.log(ProcessLog.error()
                              .withFormattedMessage("Failed to start scheduled job %s (%s) for user %s: %s",
                                                    entry,
                                                    entry.getJobConfigData().getJobName(),
                                                    UserContext.getCurrentUser().getUserName(),
                                                    exception.getMessage()));
        }
    }
}
