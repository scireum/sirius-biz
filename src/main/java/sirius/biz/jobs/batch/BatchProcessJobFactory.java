/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.jobs.BasicJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.ProcessLink;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.SpyUser;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Provides a base implementation for batch jobs which are executed as
 * {@link sirius.biz.cluster.work.DistributedTasks} within a process {@link Process}.
 * <p>
 * This will instantiate a subclass of {@link BatchJob} which will then be executed.
 */
public abstract class BatchProcessJobFactory extends BasicJobFactory {

    /**
     * Used to store and retrieve the process id which of this batch job.
     */
    public static final String CONTEXT_PROCESS = "process";

    /**
     * Used to store and retrieve the job factory which is responsible for executing the actual task.
     */
    public static final String CONTEXT_JOB_FACTORY = "jobFactory";

    /**
     * Contains a normally hidden parameter which permits to overwrite the persistence period of a job.
     * <p>
     * This is mainly used by {@link sirius.biz.storage.layer3.VFSRoot} and the
     * {@link sirius.biz.jobs.scheduler.JobSchedulerLoop} to permit to lower the persistence period of regularly
     * created processes.
     */
    public static final String HIDDEN_PARAMETER_CUSTOM_PERSISTENCE_PERIOD = "_customPersistencePeriod";

    @Part
    @Nullable
    protected Processes processes;

    @Part
    protected DistributedTasks tasks;

    @Override
    public String getIcon() {
        return "fa-solid fa-cogs";
    }

    @Override
    public boolean canStartInteractive() {
        return true;
    }

    @Override
    public void executeInteractive(WebContext request, Map<String, String> context) {
        String processId = startWithContext(context);
        request.respondWith().redirectToGet("/ps/" + processId);
    }

    @Override
    public boolean canStartInBackground() {
        return true;
    }

    @Override
    protected String executeInBackground(Map<String, String> context) {
        return startWithContext(context);
    }

    @Override
    public Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider,
                                                     boolean enforceRequiredParameters,
                                                     BiConsumer<Parameter<?>, HandledException> errorConsumer) {
        Map<String, String> context =
                super.buildAndVerifyContext(parameterProvider, enforceRequiredParameters, errorConsumer);

        parameterProvider.apply(HIDDEN_PARAMETER_CUSTOM_PERSISTENCE_PERIOD)
                         .ifFilled(Value::asString,
                                   period -> context.put(HIDDEN_PARAMETER_CUSTOM_PERSISTENCE_PERIOD, period));

        return context;
    }

    /**
     * Creates a {@link Process} and schedules a {@link sirius.biz.cluster.work.DistributedTasks task}
     * to execute the job (on this node or another one).
     *
     * @param context the parameters supplied by the user
     * @return the id of the newly created process
     */
    protected String startWithContext(Map<String, String> context) {
        String processId = processes.createProcess(getName(),
                                                   createProcessTitle(context),
                                                   getIcon(),
                                                   getCurrentOrRootUser(),
                                                   determinePersistencePeriod(context),
                                                   context);
        lockFiles(context);
        logScheduledMessage(processId);
        addLinkToJob(processId);
        createAndScheduleDistributedTask(processId);

        return processId;
    }

    private UserInfo getCurrentOrRootUser() {
        if (isSystemProcess()) {
            return UserContext.getCurrentUser()
                              .tryAs(SpyUser.class)
                              .map(SpyUser::getRootUser)
                              .orElseGet(UserContext::getCurrentUser);
        }
        return UserContext.getCurrentUser();
    }

    private PersistencePeriod determinePersistencePeriod(Map<String, String> context) {
        return Value.of(context.get(HIDDEN_PARAMETER_CUSTOM_PERSISTENCE_PERIOD))
                    .getEnum(PersistencePeriod.class)
                    .orElseGet(this::getPersistencePeriod);
    }

    /**
     * Logs an initial message to record when the job was scheduled.
     *
     * @param processId the process representing the execution of this job
     */
    protected void logScheduledMessage(String processId) {
        processes.log(processId, ProcessLog.info().withNLSKey("BatchProcessJobFactory.scheduled"));
    }

    /**
     * Adds a link for this job to the {@link Process}.
     * <p>
     * This can be overwritten to suppress this behaviour.
     *
     * @param processId the id of the process which has been created
     */
    protected void addLinkToJob(String processId) {
        if (canStartInteractive()) {
            processes.addLink(processId,
                              new ProcessLink().withLabel("$BatchProcessJobFactory.jobLink")
                                               .withUri("/job/" + getName()));
        }
    }

    private void createAndScheduleDistributedTask(String processId) {
        ObjectNode executorContext =
                Json.createObject().put(CONTEXT_PROCESS, processId).put(CONTEXT_JOB_FACTORY, getName());
        if (tasks.getQueueInfo(tasks.getQueueName(getExecutor())).isPrioritized()) {
            tasks.submitPrioritizedTask(getExecutor(), getPenaltyToken(), executorContext);
        } else {
            tasks.submitFIFOTask(getExecutor(), executorContext);
        }
    }

    /**
     * Creates the title for the {@link Process} based on the given context.
     *
     * @param context the parameters supplied by the user
     * @return the title to use for the process
     */
    protected abstract String createProcessTitle(Map<String, String> context);

    /**
     * Determines the persistence period for the generated {@link sirius.biz.process.Process}.
     *
     * @return the persistence period to apply
     */
    protected abstract PersistencePeriod getPersistencePeriod();

    /**
     * Determines if the persistence period may be changed by users. This is enabled by default.
     * <p>
     * Note that administrators may always override the persistence period.
     *
     * @return <tt>true</tt> if the persistence period may be changed, <tt>false</tt> otherwise
     */
    public boolean canChangePersistencePeriod() {
        return true;
    }

    /**
     * Returns the executor which is responsible for resolving the created {@link sirius.biz.process.Process} and
     * then invoking {@link #executeTask(ProcessContext)}.
     * <p>
     * Different executors can be used to run jobs in different queues or with different prioritization settings.
     *
     * @return the executor used to handle the queueing and eventually the execution of this job
     */
    protected abstract Class<? extends DistributedTaskExecutor> getExecutor();

    /**
     * If the queue in which the {@link #getExecutor() executor} places its tasks is <b>prioritized</b>,
     * we use this method to determine the penalty token.
     * <p>
     * This is the tenant id by default. Therefore, all tasks of the same tenant increase the penalty
     * (and thus lowers the priority of newly scheduled tasks).
     * <p>
     * Note: {@link #isSystemProcess() System processes} count towards the system tenant even if they were started
     * while spying another tenant.
     *
     * @return the penalty token to use
     */
    protected String getPenaltyToken() {
        return getCurrentOrRootUser().getTenantId();
    }

    /**
     * Determines if scriptable events are enabled for this factory.
     * <p>
     * Disabled by default. Override this method to enable scriptable events where needed.
     *
     * @return <tt>true</tt> if scriptable events should be enabled, <tt>false</tt> otherwise
     */
    protected boolean enableScriptableEvents() {
        return false;
    }

    /**
     * Indicates if the execution of this process should only be visible to the system tenant.
     * <p>
     * This helps prevent exposing system processes like database migrations or confidential evaluations to the wrong
     * tenant, when such a process was started while spying another tenant.
     *
     * @return <tt>true</tt> if the {@link TenantUserManager#PERMISSION_SYSTEM_ADMINISTRATOR system administrator}
     * permission is required, <tt>false</tt> otherwise. Override this and make it return <tt>true</tt> whenever
     * the execution of a process should only be visible to the system tenant.
     */
    protected boolean isSystemProcess() {
        return getRequiredPermissions().contains(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR);
    }

    /**
     * Executes the task on the target node and covered within the execution context of a {@link Process}.
     *
     * @param process the context of the previously generated process to communicate with the outside world
     * @throws Exception in case of any error which should abort this job
     */
    protected void executeTask(ProcessContext process) throws Exception {
        logParameters(process);
        try (BatchJob job = createJob(process)) {
            job.initializeEventDispatchers(enableScriptableEvents());
            executeJob(process, job);
        }
    }

    protected abstract BatchJob createJob(ProcessContext process) throws Exception;

    @SuppressWarnings("java:S1172")
    @Explain("The process parameter might be used by overriding subclasses.")
    protected void executeJob(ProcessContext process, BatchJob job) throws Exception {
        job.execute();
    }

    protected void logParameters(ProcessContext process) {
        List<Parameter<?>> parameters = getParameters();

        makeParameterLog(process, parameters, Parameter.LogVisibility.NORMAL).ifPresent(process::log);
        makeParameterLog(process, parameters, Parameter.LogVisibility.SYSTEM).ifPresent(processLog -> process.log(
                processLog.asSystemMessage()));
    }

    private Optional<ProcessLog> makeParameterLog(ProcessContext process,
                                                  List<Parameter<?>> parameters,
                                                  Parameter.LogVisibility logVisibility) {
        if (parameters.stream().noneMatch(parameter -> logVisibility == parameter.getLogVisibility())) {
            return Optional.empty();
        }
        StringBuilder output = new StringBuilder();
        output.append(NLS.get("ProcessLog.parameterHeading." + logVisibility.name()));
        output.append(":\n\n");

        parameters.stream().filter(parameter -> logVisibility == parameter.getLogVisibility()).forEach(param -> {
            String value = process.getParameter(param).map(NLS::toUserString).orElse("-");
            output.append(param.getLabel());
            output.append(": ");
            output.append(value);
            output.append("\n");
        });

        return Optional.of(ProcessLog.info().withMessage(output.toString().trim()));
    }
}
