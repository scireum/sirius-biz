/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting.mongo;

import sirius.biz.scripting.ScriptableEventRegistry;
import sirius.biz.scripting.ScriptingController;
import sirius.biz.web.BizController;
import sirius.biz.web.MongoPageHelper;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;
import sirius.kernel.tokenizer.Position;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.NoodleCompiler;
import sirius.pasta.noodle.compiler.SourceCodeInfo;
import sirius.pasta.noodle.sandbox.SandboxMode;
import sirius.pasta.tagliatelle.compiler.TemplateCompiler;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides the management UI for {@link MongoCustomScript custom scripts}.
 */
@Register(framework = MongoCustomEventDispatcherRepository.FRAMEWORK_SCRIPTING_MONGO)
public class MongoCustomScriptController extends BizController {

    private static final String PARAM_SCRIPT = "script";

    /**
     * Lists all scripts available for the current tenant.
     *
     * @param webContext the request to handle
     */
    @Routed("/scripting/scripts")
    @Permission(ScriptingController.PERMISSION_SCRIPTING)
    public void listScripts(WebContext webContext) {
        MongoPageHelper<MongoCustomScript> pageHelper =
                MongoPageHelper.withQuery(tenants.forCurrentTenant(mango.select(MongoCustomScript.class)
                                                                        .orderAsc(MongoCustomScript.CODE)))
                               .withContext(webContext);

        pageHelper.withSearchFields(QueryField.startsWith(MongoCustomScript.SEARCH_PREFIXES));
        webContext.respondWith().template("/templates/biz/scripting/mongo-scripts.html.pasta", pageHelper.asPage());
    }

    /**
     * Modifies / manages the given script.
     *
     * @param webContext the request to handle
     * @param id         the ID of the script to manage
     */
    @Routed("/scripting/scripts/:1")
    @Permission(ScriptingController.PERMISSION_SCRIPTING)
    public void editScript(WebContext webContext, String id) {
        MongoCustomScript script = findForTenant(MongoCustomScript.class, id);
        boolean requestHandled = prepareSave(webContext).withAfterSaveURI("/scripting/scripts").saveEntity(script);
        if (!requestHandled) {
            webContext.respondWith().template("/templates/biz/scripting/mongo-script.html.pasta", script);
        }
    }

    /**
     * Deletes the given script.
     *
     * @param webContext the request to handle
     * @param id         the ID of the script to delete
     */
    @Routed("/scripting/scripts/:1/delete")
    @Permission(ScriptingController.PERMISSION_SCRIPTING)
    public void deleteScript(WebContext webContext, String id) {
        if (webContext.isSafePOST()) {
            deleteEntity(webContext, tryFindForTenant(MongoCustomScript.class, id));
        }
        webContext.respondWith().redirectToGet("/scripting/scripts");
    }

    /**
     * Runs the compiler on a given script and reports all errors or warnings.
     *
     * @param webContext the request to handle
     * @param output     the output to write to
     */
    @Routed("/scripting/api/compile")
    @InternalService
    @Permission(ScriptingController.PERMISSION_SCRIPTING)
    public void compile(WebContext webContext, JSONStructuredOutput output) {
        if (webContext.isSafePOST()) {
            String script = webContext.get(PARAM_SCRIPT).asString();
            CompilationContext compilationContext =
                    new CompilationContext(SourceCodeInfo.forInlineCode(script, SandboxMode.DISABLED));
            compilationContext.getVariableScoper()
                              .defineVariable(Position.UNKNOWN,
                                              MongoCustomEventDispatcherRepository.SCRIPT_PARAMETER_REGISTRY,
                                              ScriptableEventRegistry.class);

            NoodleCompiler compiler = new NoodleCompiler(compilationContext);
            compiler.compileScript();
            TemplateCompiler.reportAsJson(compilationContext.getErrors(), output);
        }
    }
}
