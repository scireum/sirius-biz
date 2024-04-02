/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting.mongo;

import sirius.biz.scripting.ScriptableEventDispatcher;
import sirius.biz.scripting.ScriptableEventDispatcherRepository;
import sirius.biz.scripting.ScriptableEventRegistry;
import sirius.biz.scripting.SimpleScriptableEventDispatcher;
import sirius.db.mongo.Mango;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;
import sirius.kernel.tokenizer.Position;
import sirius.pasta.noodle.Callable;
import sirius.pasta.noodle.ScriptingException;
import sirius.pasta.noodle.SimpleEnvironment;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.NoodleCompiler;
import sirius.pasta.noodle.compiler.SourceCodeInfo;
import sirius.pasta.noodle.sandbox.SandboxMode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Stores and manages {@link ScriptableEventDispatcher custom event dispatchers} in a MongoDB.
 */
@Register(framework = MongoCustomEventDispatcherRepository.FRAMEWORK_SCRIPTING_MONGO)
public class MongoCustomEventDispatcherRepository implements ScriptableEventDispatcherRepository {

    protected static final String FRAMEWORK_SCRIPTING_MONGO = "biz.scripting-mongo";

    /**
     * Contains the name of the variable which holds the {@link ScriptableEventRegistry} in a script.
     */
    public static final String SCRIPT_PARAMETER_REGISTRY = "registry";

    @Part
    private Mango mango;

    @Override
    public List<String> fetchAvailableDispatchers(@Nonnull String tenantId) {
        return mango.select(MongoCustomScript.class)
                    .eq(MongoCustomScript.TENANT, tenantId)
                    .orderAsc(MongoCustomScript.CODE)
                    .queryList()
                    .stream()
                    .map(MongoCustomScript::getCode)
                    .toList();
    }

    @Override
    public Optional<ScriptableEventDispatcher> fetchDispatcher(@Nonnull String tenantId, @Nullable String name) {
        if (Strings.isEmpty(name)) {
            List<MongoCustomScript> mongoCustomScripts =
                    mango.select(MongoCustomScript.class).eq(MongoCustomScript.TENANT, tenantId).limit(2).queryList();
            if (mongoCustomScripts.size() == 1) {
                return compileAndLoad(mongoCustomScripts.getFirst());
            } else {
                return Optional.empty();
            }
        } else {
            return mango.select(MongoCustomScript.class)
                        .eq(MongoCustomScript.TENANT, tenantId)
                        .eq(MongoCustomScript.CODE, name)
                        .first()
                        .flatMap(this::compileAndLoad);
        }
    }

    private Optional<ScriptableEventDispatcher> compileAndLoad(MongoCustomScript script) {
        try {
            if (Strings.isEmpty(script.getScript())) {
                return Optional.empty();
            }

            CompilationContext compilationContext =
                    new CompilationContext(SourceCodeInfo.forInlineCode(script.getScript(), SandboxMode.WARN_ONLY));

            compilationContext.getVariableScoper()
                              .defineVariable(Position.UNKNOWN, SCRIPT_PARAMETER_REGISTRY, ScriptableEventRegistry.class);
            NoodleCompiler compiler = new NoodleCompiler(compilationContext);
            Callable compiledScript = compiler.compileScript();

            SimpleScriptableEventDispatcher dispatcher = new SimpleScriptableEventDispatcher();
            SimpleEnvironment environment = new SimpleEnvironment();
            environment.writeVariable(0, dispatcher);
            compiledScript.call(environment);

            return Optional.of(dispatcher);
        } catch (ScriptingException | HandledException e) {
            TaskContext.get()
                       .log("Failed compiling custom event dispatcher '%s': %s", script.getCode(), e.getMessage());
            return Optional.empty();
        }
    }
}
