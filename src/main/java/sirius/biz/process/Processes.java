/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.elastic.AutoBatchLoop;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogState;
import sirius.biz.process.logs.ProcessLogType;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.protocol.JournalData;
import sirius.db.es.Elastic;
import sirius.db.mixing.OptimisticLockException;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.DelayLine;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides the central facility to create and use {@link Process processes}.
 */
@Register(classes = Processes.class, framework = Processes.FRAMEWORK_PROCESSES)
public class Processes {

    /**
     * Names the framework which must be enabled to activate the processes feature.
     */
    public static final String FRAMEWORK_PROCESSES = "biz.processes";

    @Part
    private Elastic elastic;

    @Part
    private DelayLine delayLine;

    @Part
    private AutoBatchLoop autoBatch;

    @Part
    private ProcessFileStorage fileStorage;

    /**
     * Due to some shortcomings in Elasticsearch (1 second delay until writes are visible), we need a layered cache
     * architecture here.
     * <p>
     * This cache is very short lived and only used to provides instances which can directly be modified
     * (most probably without even waiting for the "1 second" delay, as long as only one node concurrently modifies
     * a process - which should be quite common).
     */
    private Cache<String, Process> process1stLevelCache = CacheManager.createLocalCache("processes-first-level");

    /**
     * This is a longer lived "read" cache to pull data from. This is mostly used by the {@link ProcessEnvironment}
     * to fetch "static" data (context, user, ...).
     */
    private Cache<String, Process> process2ndLevelCache = CacheManager.createCoherentCache("processes-second-level");

    /**
     * Creates a new process.
     *
     * @param title   the title to show in the UI
     * @param icon    the icon to use for this process
     * @param user    the user that belongs to the process
     * @param context the context passed into the process
     * @return the newly created process
     */
    public String createProcess(String title, String icon, UserInfo user, Map<String, String> context) {
        Process process = new Process();
        process.setTitle(title);
        process.setIcon(icon);
        process.setUserId(user.getUserId());
        process.setUserName(user.getUserName());
        process.setTenantId(user.getTenantId());
        process.setTenantName(user.getTenantName());
        process.setState(ProcessState.RUNNING);
        process.setStarted(LocalDateTime.now());
        process.getContext().modify().putAll(context);

        elastic.update(process);
        process1stLevelCache.put(process.getId(), process);
        process2ndLevelCache.put(process.getId(), process);

        return process.getIdAsString();
    }

    /**
     * Creates a new process for the currently active user.
     *
     * @param title   the title to show in the UI
     * @param icon    the icon to use for this process
     * @param context the context passed into the process
     * @return the newly created process
     */
    public String createProcessForCurrentUser(String title, String icon, Map<String, String> context) {
        return createProcess(title, icon, UserContext.getCurrentUser(), context);
    }

    /**
     * Executes the given task in the standby process of the given type, for the given tenant.
     * <p>
     * If no matching standby process exists, one will be created.
     *
     * @param type               the type of the standby process to find or create
     * @param titleSupplier      a supplier which generates a title if the process has to be created
     * @param tenantId           the id of the tenant used to find the appropriate process
     * @param tenantNameSupplier a supplier which yields the name of the tenant if the process has to be created
     * @param task               the task to execute within the process
     */
    public void executeInStandbyProcess(String type,
                                        Supplier<String> titleSupplier,
                                        String tenantId,
                                        Supplier<String> tenantNameSupplier,
                                        Consumer<ProcessContext> task) {
        String processId = elastic.select(Process.class)
                                  .eq(Process.STATE, ProcessState.STANDBY)
                                  .eq(Process.TENANT_ID, tenantId)
                                  .eq(Process.PROCESS_TYPE, type)
                                  .first()
                                  .map(Process::getId)
                                  .orElse(null);

        if (processId == null) {
            Process process = new Process();
            process.setTitle(titleSupplier.get());
            process.setProcessType(type);
            process.setTenantId(tenantId);
            process.setTenantName(tenantNameSupplier.get());
            process.setState(ProcessState.STANDBY);
            process.setStarted(LocalDateTime.now());

            elastic.update(process);
            process1stLevelCache.put(process.getId(), process);
            process2ndLevelCache.put(process.getId(), process);

            processId = process.getIdAsString();
        } else {
            modify(processId,
                   process -> process.getState() == ProcessState.STANDBY,
                   process -> process.setStarted(LocalDateTime.now()));
        }

        partiallyExecute(processId, task);
    }

    /**
     * Resolves the given id into a process.
     * <p>
     * This will first try the 1st and 2nc level cache and then resort to Elasticsearch.
     * However, a fetched process is only put into the 2nd level cache once it was resolved
     * as this has a lover lifespan and is appropriate for most reads.
     *
     * @param processId the id of the process to fetch
     * @return the process with the given id wrapped as optional or an empty optional if no such process exists
     */
    protected Optional<Process> fetchProcess(String processId) {
        Process process = process1stLevelCache.get(processId);
        if (process != null) {
            return Optional.of(process);
        }

        process = process2ndLevelCache.get(processId);
        if (process != null) {
            return Optional.of(process);
        }

        process = elastic.find(Process.class, processId).orElse(null);
        if (process != null) {
            // Only cache non-null lookups...
            process2ndLevelCache.put(processId, process);
        }

        return Optional.ofNullable(process);
    }

    /**
     * Modifies the process with the given id.
     * <p>
     * This utilizes the 1st level cache to lookup recently modified objects without having to
     * wait for the 1s write delay.
     *
     * @param processId the id of the process to modify
     * @param checker   the predicate to evaluate if the modification should take place
     * @param modifier  the actual modifier which mutates the process
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    private boolean modify(String processId, Predicate<Process> checker, Consumer<Process> modifier) {
        Process process = process1stLevelCache.get(processId);
        if (process == null) {
            process = elastic.find(Process.class, processId).orElse(null);
        }

        if (process == null) {
            return false;
        }

        int retries = 5;
        while (retries-- > 0) {
            if (!checker.test(process)) {
                return false;
            }
            modifier.accept(process);
            try {
                elastic.tryUpdate(process);
                process1stLevelCache.put(processId, process);
                process2ndLevelCache.put(processId, process);
                return true;
            } catch (OptimisticLockException e) {
                Wait.randomMillis(250, 500);
            }
        }

        Log.BACKGROUND.WARN("Failed to update process %s after 5 attempts. Skipping update...", processId);
        return false;
    }

    /**
     * Updates the state of the given process.
     *
     * @param processId the process to update
     * @param newState  the new state of the process
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean updateState(String processId, ProcessState newState) {
        return modify(processId, null, process -> process.setState(newState));
    }

    /**
     * Marks a process as canceled.
     * <p>
     * Note that this also marks the process as {@link Process#ERRORNEOUS}.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markCanceled(String processId) {
        return modify(processId, process -> process.getState() == ProcessState.RUNNING, process -> {
            process.setErrorneous(true);
            process.setCanceled(LocalDateTime.now());
            process.setState(ProcessState.CANCELED);
        });
    }

    /**
     * Marks a process as errorneous.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markErrorneous(String processId) {
        return modify(processId,
                      process -> !process.isErrorneous() && process.getState() == ProcessState.RUNNING,
                      process -> process.setErrorneous(true));
    }

    /**
     * Marks a process as completed.
     *
     * @param processId the process to update
     * @param timings   timing which have been collected and not yet committed
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markCompleted(String processId, @Nullable Map<String, String> timings) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.setState(ProcessState.TERMINATED);
            process.setCompleted(LocalDateTime.now());

            if (timings != null) {
                process.getCounters().modify().putAll(timings);
            }
        });
    }

    /**
     * Updates the performance counters of the given process.
     *
     * @param processId the process to update
     * @param timings   the timings (label, value) to store
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean addTimings(String processId, Map<String, String> timings) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getCounters().modify().putAll(timings);
        });
    }

    /**
     * Specifies the state message of the given process.
     *
     * @param processId the process to update
     * @param state     the state message to set
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean setStateMessage(String processId, String state) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.setStateMessage(state);
        });
    }

    /**
     * Adds the given link to the given process.
     *
     * @param processId the process to update
     * @param link      the link to add
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean addLink(String processId, ProcessLink link) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getLinks().add(link);
        });
    }

    /**
     * Adds the given output to the given process.
     *
     * @param processId the process to update
     * @param output    the output to add
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */

    public boolean addOutput(String processId, ProcessOutput output) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getOutputs().add(output);
        });
    }

    /**
     * Adds a file to the given process.
     *
     * @param processId the process to update
     * @param filename  the filename to use
     * @param data      the data to persist
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */

    public boolean addFile(String processId, String filename, File data) {
        Process process = fetchProcess(processId).orElse(null);
        if (process == null) {
            return false;
        }

        ProcessFile file = getStorage().upload(process, filename, data);
        return modify(processId, proc -> true, proc -> {
            proc.getFiles().add(file);
        });
    }

    //TODO maybe use Storage
    public ProcessFileStorage getStorage() {
        return fileStorage;
    }

    /**
     * Stores the log entry for the given process.
     * <p>
     * Note that this will be done asynchronously to permit bulk inserts.
     *
     * @param processId the process to store the entry for
     * @param logEntry  the entry to persist
     */
    public void log(String processId, ProcessLog logEntry) {
        try {
            if (logEntry.getType() == ProcessLogType.ERROR) {
                markErrorneous(processId);
            }

            logEntry.setNode(CallContext.getNodeName());
            logEntry.setTimestamp(LocalDateTime.now());
            logEntry.setSortKey(System.currentTimeMillis());
            logEntry.getProcess().setId(processId);
            logEntry.getDescriptor().beforeSave(logEntry);
            autoBatch.insertAsync(logEntry);
        } catch (Exception e) {
            Exceptions.handle()
                      .withSystemErrorMessage("Failed to record a ProcessLog: %s - %s (%s)", logEntry)
                      .error(e)
                      .to(Log.BACKGROUND)
                      .handle();
        }
    }

    /**
     * Executes the given task "within" the given process.
     *
     * @param processId the process to execute within
     * @param task      the task to execute
     * @param complete  <tt>true</tt> to mark the process as completed once the task is done, <tt>false</tt> otherwise
     */
    private void execute(String processId, Consumer<ProcessContext> task, boolean complete) {
        awaitProcess(processId);
        TaskContext taskContext = TaskContext.get();
        UserContext userContext = UserContext.get();

        TaskContextAdapter taskContextAdapterBackup = taskContext.getAdapter();
        UserInfo userInfoBackup = userContext.getUser();

        ProcessEnvironment env = new ProcessEnvironment(processId);
        taskContext.setJob(processId);
        taskContext.setAdapter(env);
        try {
            if (env.isActive()) {
                installUserOfProcess(userContext, env);
                task.accept(env);
            }
        } catch (Exception e) {
            env.handle(e);
        } finally {
            taskContext.setAdapter(taskContextAdapterBackup);
            userContext.setCurrentUser(userInfoBackup);
            if (complete) {
                env.markCompleted();
            }
        }
    }

    /**
     * Await until the process really exists.
     * <p>
     * As {@link #createProcess(String, String, UserInfo, Map)} performs an insert into ES without any delay,
     * the same process might not yet be visible on another node (due to the 1s insert delay of ES). Therefore
     * we check the existence of the process and wait a certain amount of time if it doesn't exist.
     * <p>
     * Note that this isn't necessarry on the same node and therefore actually bypassed, as the 1st level
     * cache will be properly populated and therefore this check will immediatelly succeed.
     *
     * @param processId the process to check
     */
    private void awaitProcess(String processId) {
        if (!fetchProcess(processId).isPresent()) {
            Wait.millis(1200);
            if (!fetchProcess(processId).isPresent()) {
                throw new IllegalStateException("Unknown process id: " + processId);
            }
        }
    }

    /**
     * Installs the user defined by the process (into the {@link UserContext}).
     * <p>
     * If no user is attached to the process, no modification will be performed.
     *
     * @param userContext the context to update
     * @param env         the process environment to read the user infos from
     */
    private void installUserOfProcess(UserContext userContext, ProcessEnvironment env) {
        if (env.getUserId() != null) {
            UserInfo user = userContext.getUserManager().findUserByUserId(env.getUserId());
            if (user != null) {
                user = userContext.getUserManager().createUserWithTenant(user, env.getTenantId());
                userContext.setCurrentUser(user);
            }
        }
    }

    /**
     * Executes the given task in the given process without marking it as {@link ProcessState#TERMINATED}.
     *
     * @param processId the process to execute the task in
     * @param task      the task to execute
     */
    public void partiallyExecute(String processId, Consumer<ProcessContext> task) {
        execute(processId, task, false);
    }

    /**
     * Executes the given task in the given process and then marks it as {@link ProcessState#TERMINATED}.
     *
     * @param processId the process to execute the task in
     * @param task      the task to execute
     */
    public void execute(String processId, Consumer<ProcessContext> task) {
        execute(processId, task, true);
    }

    /**
     * Generates a JSON representation of the given process.
     *
     * @param processId the process to output
     * @param out       the target to write the JSON to
     */
    public void outputAsJSON(String processId, JSONStructuredOutput out) {
        Process process = fetchProcessForUser(processId).orElseThrow(() -> Exceptions.createHandled()
                                                                                     .withSystemErrorMessage(
                                                                                             "Unknown process id: %s",
                                                                                             processId)
                                                                                     .handle());
        out.property("id", processId);
        out.property("title", process.getTitle());
        out.property("state", process.getState());
        out.property("started", process.getStarted());
        out.property("completed", process.getCompleted());
        out.property("errorneous", process.isErrorneous());
        out.property("processType", process.getProcessType());
        out.property("stateMessage", process.getStateMessage());
        out.beginArray("counters");
        for (Map.Entry<String, String> counter : process.getCounters()) {
            out.beginObject("counter");
            out.property("name", counter.getKey());
            out.property("value", counter.getValue());
            out.endObject();
        }
        out.endArray();
        out.beginArray("links");
        for (ProcessLink link : process.getLinks()) {
            out.beginObject("link");
            out.property("label", link.getLabel());
            out.property("uri", link.getUri());
            out.endObject();
        }
        out.endArray();
    }

    /**
     * Resolves the id into a process while ensuring that the current user may access it.
     * <p>
     * Note that this will not utilize the 1st and 2nd level cache as it is intended for UI (read) access.
     *
     * @param processId the id to resolve into a process
     * @return the resolved process wrapped as optional or an empty optional if there is no such process
     * or the user may not access it.
     */
    public Optional<Process> fetchProcessForUser(String processId) {
        Optional<Process> process = elastic.find(Process.class, processId);
        if (!process.isPresent()) {
            // Maybe the given process id was just created and not visible in ES.
            // Wait for a good second and retry once...
            Wait.millis(1200);
            process = elastic.find(Process.class, processId);
            if (!process.isPresent()) {
                return Optional.empty();
            }
        }

        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            if (!Objects.equals(user.getTenantId(), process.get().getTenantId())) {
                return Optional.empty();
            }
        }

        if (!Strings.areEqual(user.getUserId(), process.get().getUserId())) {
            if (user.hasPermission(ProcessController.PERMISSION_MANAGE_PROCESSES)) {
                return Optional.empty();
            }
        }

        if (!user.hasPermission(process.get().getRequiredPermission())) {
            return Optional.empty();
        }

        return process;
    }

    /**
     * Resolves the id into a log entry while ensuring that the current user may access it.
     *
     * @param processLogId the id to resolve into a log entry
     * @return the resolved log entry wrapped as optional or an empty optional if there is no such log entry
     * or the user may not access it.
     */
    public Optional<ProcessLog> fetchProcessLogForUser(String processLogId) {
        Optional<ProcessLog> processLog = elastic.find(ProcessLog.class, processLogId);
        if (!processLog.isPresent()) {
            return Optional.empty();
        }
        if (!fetchProcessForUser(processLog.get().getProcess().getId()).isPresent()) {
            return Optional.empty();
        }

        return processLog;
    }

    /**
     * Updates the state of the given process log entry and returns to the given URL be redirecting the given request.
     * <p>
     * Note that this will also emit an {@link sirius.biz.protocol.JournalEntry} to record that the current user
     * changed the state of the given log entry.
     * <p>
     * Also note, that this method doesn't perform any access checks, therefore the caller has to ensure that the
     * current user may modify the given log entry.
     *
     * @param processLog the log entry to modify
     * @param newState   the new state to set
     * @param ctx        the request to respond to
     * @param returnUrl  the URL to redirect the request to once the modification has been performed and is visible
     */
    public void updateProcessLogStateAndReturn(ProcessLog processLog,
                                               ProcessLogState newState,
                                               WebContext ctx,
                                               String returnUrl) {
        processLog.withState(newState);
        elastic.update(processLog);
        JournalData.addJournalEntry(processLog, NLS.get("ProcessLog.state") + ": " + newState.toString());
        delayLine.forkDelayed(Tasks.DEFAULT, 1, () -> ctx.respondWith().redirectToGet(returnUrl));
    }
}
