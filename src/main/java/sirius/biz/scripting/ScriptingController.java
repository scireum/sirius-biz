/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.CompileException;
import sirius.pasta.noodle.compiler.NoodleCompiler;
import sirius.pasta.noodle.compiler.SourceCodeInfo;
import sirius.pasta.noodle.sandbox.SandboxMode;
import sirius.pasta.tagliatelle.compiler.TemplateCompiler;
import sirius.web.controller.Routed;
import sirius.web.health.Cluster;
import sirius.web.health.NodeInfo;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides the UI used to submit administrative scripts and to view the transcript.
 */
@Register
public class ScriptingController extends BizController {

    /**
     * Specifies the permission which is required to execute scripts.
     */
    public static final String PERMISSION_SCRIPTING = "permission-system-scripting";

    private static final String PARAM_SCRIPT = "script";
    private static final String PARAM_NODE = "node";
    private static final String PARAM_MIN_TIMESTAMP = "minTimestamp";
    private static final String RESPONSE_MESSAGES = "messages";
    private static final String RESPONSE_MESSAGE = "message";
    private static final String RESPONSE_JOB = "job";
    private static final String RESPONSE_TIMESTAMP = "timestamp";
    private static final String RESPONSE_TIMESTAMP_STRING = "timestampString";
    private static final String RESPONSE_JOB_MESSAGE = "jobMessage";

    @Part
    private Scripting scripting;

    @Part
    private Cluster cluster;

    /**
     * Renders the scripting UI.
     *
     * @param webContext the request to handle
     */
    @Routed("/system/scripting")
    @Permission(PERMISSION_SCRIPTING)
    public void scripting(WebContext webContext) {
        List<Tuple<String, String>> nodes = cluster.getNodeInfos()
                                                   .stream()
                                                   .map(NodeInfo::getName)
                                                   .map(name -> Tuple.create(name, name))
                                                   .collect(Collectors.toList());

        nodes.addFirst(Tuple.create("Current Machine", Scripting.LOCAL_NODE));
        nodes.add(Tuple.create("All Machines", Scripting.ALL_NODES));

        webContext.respondWith().template("/templates/biz/scripting/scripting.html.pasta", nodes);
    }

    /**
     * Outputs all recorded transcript messages since the given timestamp.
     *
     * @param webContext the request to handle
     * @param output     the output to write to
     */
    @Routed("/system/scripting/api/transcript")
    @InternalService
    @Permission(PERMISSION_SCRIPTING)
    public void transcript(WebContext webContext, JSONStructuredOutput output) {
        long minTimestamp = webContext.get(PARAM_MIN_TIMESTAMP).asLong(0);
        output.beginArray(RESPONSE_MESSAGES);
        for (TranscriptMessage msg : scripting.getMessages()) {
            if (msg.getTimestamp() > minTimestamp) {
                output.beginObject(RESPONSE_MESSAGE);
                output.property(RESPONSE_JOB, Strings.leftPad(msg.getJobNumber(), " ", 6));
                output.property(RESPONSE_TIMESTAMP, msg.getTimestamp());
                output.property(RESPONSE_TIMESTAMP_STRING, NLS.toUserString(Instant.ofEpochMilli(msg.getTimestamp())));
                output.property(PARAM_NODE, msg.getNode());
                output.property(RESPONSE_MESSAGE, msg.getMessage());
                output.endObject();
            }
        }
        output.endArray();
    }

    /**
     * Submits a script to execute.
     *
     * @param webContext the request to handle
     * @param output     the output to write to
     * @throws Exception in case of a compilation error
     */
    @Routed("/system/scripting/api/submit")
    @InternalService
    @Permission(PERMISSION_SCRIPTING)
    public void submit(WebContext webContext, JSONStructuredOutput output) throws Exception {
        try {
            if (webContext.isSafePOST()) {
                String script = webContext.get(PARAM_SCRIPT).asString();
                String targetNode = webContext.get(PARAM_NODE).asString();

                CompilationContext compilationContext =
                        new CompilationContext(SourceCodeInfo.forInlineCode(script, SandboxMode.DISABLED));
                NoodleCompiler compiler = new NoodleCompiler(compilationContext);
                compiler.compileScript();
                compilationContext.processCollectedErrors();

                String jobNumber = scripting.submitScript(script, targetNode);
                output.property(RESPONSE_JOB_MESSAGE,
                                NLS.fmtr("ScriptingController.jobMessage").set(RESPONSE_JOB, jobNumber).format());
            }
        } catch (CompileException exception) {
            throw Exceptions.createHandled().withDirectMessage(exception.getMessage()).handle();
        }
    }

    /**
     * Runs the compiler on a given script and reports all errors or warnings.
     *
     * @param webContext the request to handle
     * @param output     the output to write to
     */
    @Routed("/system/scripting/api/compile")
    @InternalService
    @Permission(PERMISSION_SCRIPTING)
    public void compile(WebContext webContext, JSONStructuredOutput output) {
        if (webContext.isSafePOST()) {
            String script = webContext.get(PARAM_SCRIPT).asString();
            CompilationContext compilationContext =
                    new CompilationContext(SourceCodeInfo.forInlineCode(script, SandboxMode.DISABLED));
            NoodleCompiler compiler = new NoodleCompiler(compilationContext);
            compiler.compileScript();
            TemplateCompiler.reportAsJson(compilationContext.getErrors(), output);
        }
    }
}
