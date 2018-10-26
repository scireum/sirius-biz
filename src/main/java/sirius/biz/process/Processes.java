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
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
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

    private Cache<String, Process> processCache = CacheManager.createCoherentCache("processes");

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
        return Optional.of(processCache.get(processId, id -> elastic.find(Process.class, id).orElse(null)));
    }

    private void modify(String processId,
                        Function<Process, Boolean> checker,
                        Consumer<Process> modifier,
                        boolean flush) {
        if (flush) {
            processCache.remove(processId);
        }
    }

    private void modifyAndFlush(String processId, Function<Process, Boolean> checker, Consumer<Process> modifier) {
        modify(processId, checker, modifier, true);
    }

    private void modifyWithoutFlush(String processId, Function<Process, Boolean> checker, Consumer<Process> modifier) {
        modify(processId, checker, modifier, false);
    }

    protected void updateStatus(String processId, ProcessState newState) {
        modifyAndFlush(processId,
                       process -> process.getState().ordinal() < newState.ordinal(),
                       process -> process.setState(newState));
    }

    protected void markStarted(String processId) {
        modifyAndFlush(processId, process -> process.getState() == ProcessState.SCHEDULED, process -> {
            process.setState(ProcessState.RUNNING);
            process.setStarted(LocalDateTime.now());
        });
    }

    protected void markCanceled(String processId) {
        modifyAndFlush(processId,
                       process -> process.getState() == ProcessState.SCHEDULED
                                  || process.getState() == ProcessState.RUNNING,
                       process -> process.setState(ProcessState.CANCELED));
    }

    protected void markCompleted(String processId, ProcessCompletionType completionType, Map<String, String> timings) {
        modifyAndFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.setState(ProcessState.TERMINATED);
            process.setCompleted(LocalDateTime.now());
            process.setCompletionType(completionType);
            if (timings != null) {
                process.getCounters().modify().putAll(timings);
            }
        });
    }

    protected void addTimings(String processId, Map<String, String> timings) {
        modifyWithoutFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.getCounters().modify().putAll(timings);
        });
    }

    protected void setStateMessage(String processId, String state) {
        modifyWithoutFlush(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            process.setStateMessage(state);
        });
    }

    public void partiallyExecute(String processId, Consumer<ProcessContext> task) {
        TaskContext taskContext = TaskContext.get();
        UserContext userContext = UserContext.get();

        TaskContextAdapter taskContextAdapterBackup = taskContext.getAdapter();
        UserInfo userInfoBackup = userContext.getUser();

        ProcessEnvironment env = new ProcessEnvironment();
        taskContext.setJob(env.getProcess().getIdAsString());
        taskContext.setAdapter(env);

        UserInfo user = userContext.getUserManager().findUserByUserId(env.getProcess().getUserId());
        if (user != null) {
            userContext.setCurrentUser(user);
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
        }
    }

    public void execute(String processId, Consumer<ProcessContext> task) {
        partiallyExecute(processId, ctx -> {
            task.accept(ctx);
            ctx.markCompleted();
        });
    }
}
