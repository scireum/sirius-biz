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
import sirius.biz.tenants.mongo.MongoTenants;
import sirius.db.mongo.Mango;
import sirius.db.mongo.QueryBuilder;
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
import java.util.List;
import java.util.Optional;

/**
 * Stores and manages {@link ScriptableEventDispatcher custom event dispatchers} in a MongoDB.
 */
@Register(framework = MongoCustomEventDispatcherRepository.FRAMEWORK_SCRIPTING_MONGO)
public class MongoCustomEventDispatcherRepository implements ScriptableEventDispatcherRepository {

    /**
     * Defines the framework which uses MongoDB to store and provide script based event dispatchers.
     */
    public static final String FRAMEWORK_SCRIPTING_MONGO = "biz.scripting-mongo";

    /**
     * Contains the name of the variable which holds the {@link ScriptableEventRegistry} in a script.
     */
    public static final String SCRIPT_PARAMETER_REGISTRY = "registry";

    @Part
    private Mango mango;

    @Part
    private MongoTenants tenants;

    @Override
    public List<ScriptableEventDispatcher> fetchDispatchers(@Nonnull String tenantId) {
        return mango.select(MongoCustomScript.class)
                    .where(QueryBuilder.FILTERS.oneInField(MongoCustomScript.TENANT,
                                                           tenants.fetchAllParentIds(tenantId)).build())
                    .eq(MongoCustomScript.DISABLED, false)
                    .queryList()
                    .stream()
                    .map(this::compileAndLoad)
                    .flatMap(Optional::stream)
                    .toList();
    }

    private Optional<ScriptableEventDispatcher> compileAndLoad(MongoCustomScript script) {
        try {
            if (Strings.isEmpty(script.getScript())) {
                return Optional.empty();
            }

            CompilationContext compilationContext =
                    new CompilationContext(SourceCodeInfo.forInlineCode(script.getScript(), SandboxMode.DISABLED));

            compilationContext.getVariableScoper()
                              .defineVariable(Position.UNKNOWN,
                                              SCRIPT_PARAMETER_REGISTRY,
                                              ScriptableEventRegistry.class);
            NoodleCompiler compiler = new NoodleCompiler(compilationContext);
            Callable compiledScript = compiler.compileScript();

            SimpleScriptableEventDispatcher dispatcher = new SimpleScriptableEventDispatcher();
            SimpleEnvironment environment = new SimpleEnvironment();
            environment.writeVariable(0, dispatcher);
            compiledScript.call(environment);

            return Optional.of(dispatcher);
        } catch (ScriptingException | HandledException exception) {
            TaskContext.get()
                       .log("Failed compiling custom event dispatcher '%s': %s",
                            script.getCode(),
                            exception.getMessage());
            return Optional.empty();
        }
    }
}
