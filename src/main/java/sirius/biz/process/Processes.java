/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.elastic.AutoBatchLoop;
import sirius.db.es.Elastic;
import sirius.db.mixing.OptimisticLockException;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Register(classes = Processes.class)
public class Processes {

    @Part
    private Elastic elastic;

    @Part
    private AutoBatchLoop autoBatch;

    private Cache<String, Process> process1stLevelCache = CacheManager.createLocalCache("processes-first-level");
    private Cache<String, Process> process2ndLevelCache = CacheManager.createCoherentCache("processes-second-level");

    public String createProcess(String title, UserInfo user, Map<String, String> context) {
        Process process = new Process();
        process.setTitle(title);
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

    public String createProcessForCurrentUser(String title, Map<String, String> context) {
        return createProcess(title, UserContext.getCurrentUser(), context);
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

        execute(processId, task, false);
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

    public void log(String processId, ProcessLog logEntry) {
        try {
            if (logEntry.getType() == ProcessLogType.ERROR) {
                markErrorneous(processId);
            }

            logEntry.setNode(CallContext.getNodeName());
            logEntry.setTimestamp(LocalDateTime.now());
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

        if (env.getUserId() != null) {
            UserInfo user = userContext.getUserManager().findUserByUserId(env.getUserId());
            if (user != null) {
                userContext.setCurrentUser(user);
            }
        }

        try {
            if (env.isActive()) {
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
}
