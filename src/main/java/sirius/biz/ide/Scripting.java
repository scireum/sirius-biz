/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.ide;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.Interconnect;
import sirius.biz.cluster.InterconnectHandler;
import sirius.biz.tenants.Tenants;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.pasta.Pasta;
import sirius.pasta.noodle.Callable;
import sirius.pasta.noodle.ScriptingException;
import sirius.pasta.noodle.SimpleEnvironment;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.CompileException;
import sirius.pasta.noodle.compiler.NoodleCompiler;
import sirius.pasta.noodle.compiler.SourceCodeInfo;
import sirius.pasta.noodle.sandbox.SandboxMode;
import sirius.web.health.Cluster;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a simple helper to execute <tt>Noodle</tt> scripts on this or any other node of the cluster.
 * <p>
 * This is mainly used for administrative tasks. Being cluster aware, we can run a script and forward
 * its output to other nodes, so that the UI can easily be updated.
 * <p>
 * Note however, that this communication is performed via our central interconnect and thus shouldn't
 * be used too heavy (like outputting 1000s of lines per second).
 */
@Register(classes = {Scripting.class, InterconnectHandler.class})
public class Scripting implements InterconnectHandler {

    private static final int MAX_MESSAGES = 256;

    private static final String TASK_TYPE = "type";
    private static final String TASK_TYPE_EXEC = "exec";
    private static final String TASK_SCRIPT = "script";
    private static final String TASK_NODE = "node";
    private static final String TASK_JOB = "job";
    private static final String TASK_TIMESTAMP = "timestamp";
    private static final String TASK_MESSAGE = "message";
    private static final String TASK_TYPE_MSG = "msg";

    /**
     * Represents a special node name which identifies "this node".
     */
    public static final String LOCAL_NODE = "-";

    /**
     * Represents a special node name which represents "all nodes".
     */
    public static final String ALL_NODES = "*";

    /**
     * Imposes an upper limit on the message length for transcript messages.
     * <p>
     * As these are distributed across the cluster via redis, it would be fatal if a huge message were to be sent
     * to all nodes. Therefore, we limit to a bearable and sane size.
     */
    private static final int MAX_TRANSCRIPT_MESSAGE_LENGTH = 32 * 1024;

    private final List<TranscriptMessage> messages = new ArrayList<>();

    @Part
    private Tasks tasks;

    @Part
    private Interconnect interconnect;

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    /**
     * Logs a message in the transcript.
     * <p>
     * This is shared across all cluster members and thus available on any node and not just the one which runs
     * the script.
     *
     * @param jobNumber the unique ID of the job
     * @param message   the message to log
     * @see JobTaskContextAdapter
     */
    public void logInTranscript(String jobNumber, String message) {
        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(TASK_TYPE, TASK_TYPE_MSG)
                                              .fluentPut(TASK_MESSAGE,
                                                         Strings.limit(message, MAX_TRANSCRIPT_MESSAGE_LENGTH, true))
                                              .fluentPut(TASK_TIMESTAMP, System.currentTimeMillis())
                                              .fluentPut(TASK_NODE, CallContext.getNodeName())
                                              .fluentPut(TASK_JOB, jobNumber));
    }

    /**
     * Submits a script for execution.
     * <p>
     * The given node can be any node name of a cluster machine. It can also be left empty to run on the
     * current machine, or it can be set to "*" to run on all machines simultaneously.
     *
     * @param script     the script to run
     * @param targetNode the target node to run on
     * @return the job ID for the execution
     * @throws HandledException if the given script cannot be compiled
     */
    public String submitScript(String script, @Nullable String targetNode) {
        CompilationContext compilationContext =
                new CompilationContext(SourceCodeInfo.forInlineCode(script, SandboxMode.DISABLED));
        NoodleCompiler compiler = new NoodleCompiler(compilationContext);
        compiler.compileScript();

        if (Scripting.LOCAL_NODE.equals(targetNode)) {
            targetNode = CallContext.getNodeName();
        }

        String jobNumber = Hasher.md5()
                                 .hash(script)
                                 .hash(targetNode)
                                 .hash(LocalDateTime.now().toString())
                                 .toHexString()
                                 .substring(0, 6);

        // For audit and tracing purposes we also log this into the system logs...
        Cluster.LOG.INFO("Executing administrative script %s for %s on '%s':%n%s",
                         jobNumber,
                         UserContext.getCurrentUser().getUserName(),
                         targetNode,
                         script);

        logInTranscript(jobNumber,
                        Strings.apply("Submitting %s on %s for user %s",
                                      jobNumber,
                                      CallContext.getNodeName(),
                                      UserContext.getCurrentUser().getUserName()));

        interconnect.dispatch(getName(),
                              new JSONObject().fluentPut(TASK_TYPE, TASK_TYPE_EXEC)
                                              .fluentPut(TASK_SCRIPT, script)
                                              .fluentPut(TASK_NODE, targetNode)
                                              .fluentPut(TASK_JOB, jobNumber));

        return jobNumber;
    }

    @Override
    public void handleEvent(JSONObject event) {
        if (TASK_TYPE_MSG.equals(event.getString(TASK_TYPE))) {
            handleMessageTask(event);
        } else if (TASK_TYPE_EXEC.equals(event.getString(TASK_TYPE))) {
            tasks.defaultExecutor().start(() -> handleExecTask(event));
        }
    }

    private void handleMessageTask(JSONObject event) {
        synchronized (messages) {
            messages.add(new TranscriptMessage(event.getString(TASK_NODE),
                                               event.getString(TASK_JOB),
                                               event.getLong(TASK_TIMESTAMP),
                                               event.getString(TASK_MESSAGE)));
            if (messages.size() > MAX_MESSAGES) {
                messages.remove(0);
            }
        }
    }

    /**
     * Provides access to the last 250 messages of the transcript.
     *
     * @return the last message which were recorded in the transcript
     */
    public List<TranscriptMessage> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    private void handleExecTask(JSONObject event) {
        String nodeName = event.getString(TASK_NODE);
        String jobNumber = event.getString(TASK_JOB);
        if (!ALL_NODES.equals(nodeName) && !Strings.areEqual(CallContext.getNodeName(), nodeName)) {
            return;
        }

        // Run as System Tenant if possible...
        if (tenants != null) {
            try {
                tenants.runAsAdmin(() -> handleTaskForNode(event, jobNumber));
            } catch (Exception e) {
                Exceptions.handle()
                          .to(Log.SYSTEM)
                          .error(e)
                          .withSystemErrorMessage("A fatal error occurred in task %s: %s (%s)", jobNumber)
                          .handle();
            }
        } else {
            // or without any special user, if the tenants framework isn't available...
            handleTaskForNode(event, jobNumber);
        }
    }

    private void handleTaskForNode(JSONObject event, String jobNumber) {
        Watch watch = Watch.start();
        logInTranscript(jobNumber,
                        Strings.apply("Starting execution on %s (Thread Id: %s / Thread Name: %s)",
                                      CallContext.getNodeName(),
                                      Thread.currentThread().getId(),
                                      Thread.currentThread().getName()));
        try {
            Callable callable = compileScript(event);

            TaskContext.get().setAdapter(new JobTaskContextAdapter(this, jobNumber));
            callable.call(new SimpleEnvironment());
        } catch (CompileException | ScriptingException | HandledException e) {
            logInTranscript(jobNumber, e.getMessage());
        } catch (Exception e) {
            logInTranscript(jobNumber, Exceptions.handle(Pasta.LOG, e).getMessage());
        }

        logInTranscript(jobNumber, Strings.apply("Execution completed (%s)", watch.duration()));
    }

    private Callable compileScript(JSONObject event) throws CompileException {
        CompilationContext compilationContext =
                new CompilationContext(SourceCodeInfo.forInlineCode(event.getString(TASK_SCRIPT),
                                                                    SandboxMode.DISABLED));
        NoodleCompiler compiler = new NoodleCompiler(compilationContext);
        Callable callable = compiler.compileScript();
        compilationContext.processCollectedErrors();
        return callable;
    }

    @Nonnull
    @Override
    public String getName() {
        return "scripting";
    }
}
