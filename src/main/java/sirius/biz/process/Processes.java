/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import com.alibaba.fastjson.JSONObject;
import sirius.db.es.Elastic;
import sirius.db.mixing.OptimisticLockException;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

@Register(classes = Processes.class)
public class Processes {

    @Part
    private Elastic elastic;

    private Cache<String, Process> process1stLevelCache = CacheManager.createLocalCache("processes-first-level");
    private Cache<String, Process> process2ndLevelCache = CacheManager.createCoherentCache("processes-second-level");

    public String createProcess(String title, UserInfo user, JSONObject context) {
        Process process = new Process();
        process.setTitle(title);
        process.setUserId(user.getUserId());
        process.setUserName(user.getUserName());
        process.setTenantId(user.getTenantId());
        process.setTenantName(user.getTenantName());
        process.setStarted(LocalDateTime.now());
        process.setState(ProcessState.SCHEDULED);
        process.setScheduled(LocalDateTime.now());
        process.setContext(context.toJSONString());

        elastic.update(process);

        // Ensure that the process id is available and visible in Elasticsearch
        Wait.millis(1500);

        return process.getIdAsString();
    }

    public String createProcessForCurrentUser(String title, JSONObject context) {
        return createProcess(title, UserContext.getCurrentUser(), context);
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

    protected boolean markStarted(String processId) {
        return modifyAndFlush(processId, process -> process.getState() == ProcessState.SCHEDULED, process -> {
            process.setState(ProcessState.RUNNING);
            process.setStarted(LocalDateTime.now());
        });
    }

    protected boolean markCanceled(String processId) {
        return modifyAndFlush(processId,
                              process -> process.getState() == ProcessState.SCHEDULED
                                         || process.getState() == ProcessState.RUNNING,
                              process -> {
                                  process.setErrorneous(true);
                                  process.setCanceled(LocalDateTime.now());
                                  process.setState(ProcessState.CANCELED);
                              });
    }

    protected boolean markErrorneous(String processId) {
        return modifyAndFlush(processId,
                              process -> !process.isErrorneous() && (process.getState() == ProcessState.SCHEDULED
                                                                     || process.getState() == ProcessState.RUNNING),
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

    private void execute(String processId, Consumer<ProcessContext> task, boolean complete) {
        TaskContext taskContext = TaskContext.get();
        UserContext userContext = UserContext.get();

        TaskContextAdapter taskContextAdapterBackup = taskContext.getAdapter();
        UserInfo userInfoBackup = userContext.getUser();

        ProcessEnvironment env = new ProcessEnvironment(processId);
        taskContext.setJob(processId);
        taskContext.setAdapter(env);

        UserInfo user = userContext.getUserManager().findUserByUserId(env.getUserId());
        if (user != null) {
            userContext.setCurrentUser(user);
        }

        try {
            markStarted(processId);
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
}
