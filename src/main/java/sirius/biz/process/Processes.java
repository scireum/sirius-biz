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

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Register(classes = Processes.class)
public class Processes {

    @Part
    private Elastic elastic;

    @Part
    private DelayLine delayLine;

    @Part
    private AutoBatchLoop autoBatch;

    private Cache<String, Process> process1stLevelCache = CacheManager.createLocalCache("processes-first-level");
    private Cache<String, Process> process2ndLevelCache = CacheManager.createCoherentCache("processes-second-level");

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

        // Ensure that the process id is available and visible in Elasticsearch
        Wait.millis(1500);

        return process.getIdAsString();
    }

    public String createProcessForCurrentUser(String title, String icon, Map<String, String> context) {
        return createProcess(title, icon, UserContext.getCurrentUser(), context);
    }

    public void executeInStandbyProcess(String type,
                                        Supplier<String> titleSupplier,
                                        UserInfo user,
                                        Consumer<ProcessContext> task) {
        String processId = elastic.select(Process.class)
                                  .eq(Process.STATE, ProcessState.STANDBY)
                                  .eq(Process.TENANT_ID, user.getTenantId())
                                  .eq(Process.PROCESS_TYPE, type)
                                  .first()
                                  .map(Process::getId)
                                  .orElse(null);

        if (processId == null) {
            Process process = new Process();
            process.setTitle(titleSupplier.get());
            process.setProcessType(type);
            process.setTenantId(user.getTenantId());
            process.setTenantName(user.getTenantName());
            process.setState(ProcessState.STANDBY);
            process.setStarted(LocalDateTime.now());

            elastic.update(process);

            // Ensure that the process id is available and visible in Elasticsearch
            Wait.millis(1500);

            processId = process.getIdAsString();
        } else {
            modifyWithoutFlush(processId,
                               process -> process.getState() == ProcessState.STANDBY,
                               process -> process.setStarted(LocalDateTime.now()));
        }

        partiallyExecute(processId, task);
    }

    protected Optional<Process> fetchProcess(String processId) {
        Process process = process1stLevelCache.get(processId);
        if (process != null) {
            return Optional.of(process);
        }

        return Optional.ofNullable(process2ndLevelCache.get(processId,
                                                            id -> elastic.find(Process.class, id).orElse(null)));
    }

    private boolean modify(String processId,
                           Function<Process, Boolean> checker,
                           Consumer<Process> modifier,
                           boolean flush) {
        Process process = process1stLevelCache.get(processId);
        if (process == null) {
            process = elastic.find(Process.class, processId).orElse(null);
        }

        if (process == null) {
            return false;
        }

        try {
            int retries = 5;
            while (retries-- > 0) {
                if (!checker.apply(process)) {
                    return false;
                }
                modifier.accept(process);
                try {
                    elastic.tryUpdate(process);
                    process1stLevelCache.put(processId, process);
                    return true;
                } catch (OptimisticLockException e) {
                    Wait.randomMillis(250, 500);
                }
            }

            Log.BACKGROUND.WARN("Failed to update process %s after 5 attempts. Skipping update...", processId);
            return false;
        } finally {
            if (flush) {
                process2ndLevelCache.remove(processId);
            }
        }
    }

    private boolean modifyAndFlush(String processId, Function<Process, Boolean> checker, Consumer<Process> modifier) {
        return modify(processId, checker, modifier, true);
    }

    private boolean modifyWithoutFlush(String processId,
                                       Function<Process, Boolean> checker,
                                       Consumer<Process> modifier) {
        return modify(processId, checker, modifier, false);
    }

    protected boolean updateState(String processId, ProcessState newState) {
        return modifyAndFlush(processId,
                              process -> process.getState().ordinal() < newState.ordinal(),
                              process -> process.setState(newState));
    }

    protected boolean markCanceled(String processId) {
        return modifyAndFlush(processId, process -> process.getState() == ProcessState.RUNNING, process -> {
            process.setErrorneous(true);
            process.setCanceled(LocalDateTime.now());
            process.setState(ProcessState.CANCELED);
        });
    }

    protected boolean markErrorneous(String processId) {
        return modifyAndFlush(processId,
                              process -> !process.isErrorneous() && process.getState() == ProcessState.RUNNING,
                              process -> process.setErrorneous(true));
    }

    protected boolean markCompleted(String processId, Map<String, String> timings) {
        return modifyAndFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.setState(ProcessState.TERMINATED);
            process.setCompleted(LocalDateTime.now());

            if (timings != null) {
                process.getCounters().modify().putAll(timings);
            }
        });
    }

    protected boolean addTimings(String processId, Map<String, String> timings) {
        return modifyWithoutFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getCounters().modify().putAll(timings);
        });
    }

    protected boolean setStateMessage(String processId, String state) {
        return modifyWithoutFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.setStateMessage(state);
        });
    }

    public boolean addLink(String processId, ProcessLink link) {
        return modifyWithoutFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getLinks().add(link);
        });
    }

    public boolean addOutput(String processId, ProcessOutput output) {
        return modifyWithoutFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getOutputs().add(output);
        });
    }

    public boolean addFile(String processId, String filename, File data) {
        Process process = fetchProcess(processId).orElse(null);
        if (process == null) {
            return false;
        }

        ProcessFile file = getStorage().upload(process, filename, data);
        return modifyWithoutFlush(processId, proc -> true, proc -> {
            proc.getFiles().add(file);
        });
    }

    @Part
    private ProcessFileStorage fileStorage;

    public ProcessFileStorage getStorage() {
        return fileStorage;
    }

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

    private void execute(String processId, Consumer<ProcessContext> task, boolean complete) {
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

    private void installUserOfProcess(UserContext userContext, ProcessEnvironment env) {
        if (env.getUserId() != null) {
            UserInfo user = userContext.getUserManager().findUserByUserId(env.getUserId());
            if (user != null) {
                user = userContext.getUserManager().createUserWithTenant(user, env.getTenantId());
                userContext.setCurrentUser(user);
            }
        }
    }

    public void partiallyExecute(String processId, Consumer<ProcessContext> task) {
        execute(processId, task, false);
    }

    public void execute(String processId, Consumer<ProcessContext> task) {
        execute(processId, task, true);
    }

    public void outputAsJSON(String processId, JSONStructuredOutput out) {
        Process process = fetchProcess(processId).orElseThrow(() -> Exceptions.createHandled()
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

    public Optional<Process> fetchProcessForUser(String processId) {
        Optional<Process> process = elastic.find(Process.class, processId);
        if (!process.isPresent()) {
            return Optional.empty();
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
