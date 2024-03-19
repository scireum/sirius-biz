/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.ide;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.cluster.Interconnect;
import sirius.biz.cluster.InterconnectHandler;
import sirius.biz.tenants.Tenants;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Json;
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
                              Json.createObject()
                                  .put(TASK_TYPE, TASK_TYPE_MSG)
                                  .put(TASK_MESSAGE, Strings.limit(message, MAX_TRANSCRIPT_MESSAGE_LENGTH, true))
                                  .put(TASK_TIMESTAMP, System.currentTimeMillis())
                                  .put(TASK_NODE, CallContext.getNodeName())
                                  .put(TASK_JOB, jobNumber));
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
        Cluster.LOG.INFO("Executing administrative script %s for %s on '%s':%n%n%s",
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
                              Json.createObject()
                                  .put(TASK_TYPE, TASK_TYPE_EXEC)
                                  .put(TASK_SCRIPT, script)
                                  .put(TASK_NODE, targetNode)
                                  .put(TASK_JOB, jobNumber));

        return jobNumber;
    }

    @Override
    public void handleEvent(ObjectNode event) {
        if (TASK_TYPE_MSG.equals(event.path(TASK_TYPE).asText())) {
            handleMessageTask(event);
        } else if (TASK_TYPE_EXEC.equals(event.path(TASK_TYPE).asText())) {
            tasks.defaultExecutor().start(() -> handleExecTask(event));
        }
    }

    private void handleMessageTask(ObjectNode event) {
        synchronized (messages) {
            messages.add(new TranscriptMessage(event.path(TASK_NODE).asText(null),
                                               event.path(TASK_JOB).asText(null),
                                               event.path(TASK_TIMESTAMP).asLong(),
                                               event.path(TASK_MESSAGE).asText(null)));
            if (messages.size() > MAX_MESSAGES) {
                messages.removeFirst();
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

    private void handleExecTask(ObjectNode event) {
        String nodeName = event.path(TASK_NODE).asText(null);
        String jobNumber = event.path(TASK_JOB).asText(null);
        if (!ALL_NODES.equals(nodeName) && !Strings.areEqual(CallContext.getNodeName(), nodeName)) {
            return;
        }

        // Run as System Tenant if possible...
        if (tenants != null) {
            try {
                tenants.runAsAdmin(() -> handleTaskForNode(event, jobNumber));
            } catch (Exception exception) {
                Exceptions.handle()
                          .to(Log.SYSTEM)
                          .error(exception)
                          .withSystemErrorMessage("A fatal error occurred in task %s: %s (%s)", jobNumber)
                          .handle();
            }
        } else {
            // or without any special user, if the tenants framework isn't available...
            handleTaskForNode(event, jobNumber);
        }
    }

    private void handleTaskForNode(ObjectNode event, String jobNumber) {
        Watch watch = Watch.start();
        logInTranscript(jobNumber,
                        Strings.apply("Starting execution on %s (Thread Id: %s / Thread Name: %s)",
                                      CallContext.getNodeName(),
                                      Thread.currentThread().threadId(),
                                      Thread.currentThread().getName()));
        try {
            Callable callable = compileScript(event);

            TaskContext.get().setAdapter(new JobTaskContextAdapter(this, jobNumber));
            callable.call(new SimpleEnvironment());
        } catch (CompileException | ScriptingException | HandledException exception) {
            logInTranscript(jobNumber, exception.getMessage());
        } catch (Exception exception) {
            logInTranscript(jobNumber, Exceptions.handle(Pasta.LOG, exception).getMessage());
        }

        logInTranscript(jobNumber, Strings.apply("Execution completed (%s)", watch.duration()));
    }

    private Callable compileScript(ObjectNode event) throws CompileException {
        CompilationContext compilationContext =
                new CompilationContext(SourceCodeInfo.forInlineCode(event.path(TASK_SCRIPT).asText(null),
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
