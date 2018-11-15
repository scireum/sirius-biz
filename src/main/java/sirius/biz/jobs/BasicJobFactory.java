/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.params.Parameter;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class BasicJobFactory implements JobFactory {

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }

    @Override
    public String getLabel() {
        return NLS.getIfExists(getClass().getSimpleName() + ".label", null).orElse(getClass().getSimpleName());
    }

    @Nullable
    @Override
    public String getDescription() {
        return NLS.getIfExists(getClass().getSimpleName() + ".description", null).orElse(null);
    }

    @Override
    public List<String> getRequiredPermissions() {
        return Arrays.stream(getClass().getAnnotationsByType(Permission.class))
                     .map(Permission::value)
                     .collect(Collectors.toList());
    }

    @Override
    public List<Parameter<?, ?>> getParameters() {
        List<Parameter<?, ?>> result = new ArrayList<>();
        collectParameters(result::add);
        return result;
    }

    protected abstract void collectParameters(Consumer<Parameter<?, ?>> parameterCollector);

    @Nullable
    @Override
    public String generatePresetUrl(Object targetObject) {
        if (targetObject == null) {
            return null;
        }

        if (!hasPresetFor(targetObject)) {
            return null;
        }
        Map<String, Object> preset = new HashMap<>();
        computePresetFor(targetObject, preset);
        StringBuilder sb = new StringBuilder("/job/");
        sb.append(getName());
        Monoflop mf = Monoflop.create();
        for (Map.Entry<String, Object> setting : preset.entrySet()) {
            sb.append(mf.firstCall() ? "?" : "&");
            sb.append(setting.getKey());
            sb.append("=");
            sb.append(Strings.urlEncode(NLS.toMachineString(setting.getValue())));
        }
        return sb.toString();
    }

    protected abstract boolean hasPresetFor(Object targetObject);

    protected abstract void computePresetFor(Object targetObject, Map<String, Object> preset);

    @Override
    public void startInUI(WebContext request) {
        checkPermissions();
        setupTaskContext();

        AtomicBoolean submit = new AtomicBoolean(request.isSafePOST());
        Map<String, String> context = buildAndVerifyContext(request::get, submit.get(), error -> {
            UserContext.message(Message.error(error));
            submit.set(false);
        });

        if (submit.get()) {
            executeInUI(request, context);
            return;
        }

        request.respondWith().template("/templates/jobs/job.html.pasta", this, context);
    }

    protected abstract void executeInUI(WebContext request, Map<String, String> context);

    @Override
    public void startInCall(WebContext request, JSONStructuredOutput out, Function<String, Value> parameterProvider) {
        checkPermissions();
        setupTaskContext();
        Map<String, String> context = buildAndVerifyContext(request::get, true, error -> {
            throw error;
        });

        executeInCall(out, context);
    }

    protected abstract void executeInCall(JSONStructuredOutput out, Map<String, String> context);

    @Override
    public void startInBackground(Function<String, Value> parameterProvider) {
        checkPermissions();
        setupTaskContext();
        Map<String, String> context = buildAndVerifyContext(parameterProvider, true, error -> {
            throw error;
        });

        executeInBackground(context);
    }

    protected abstract void executeInBackground(Map<String, String> context);

    protected void setupTaskContext() {
        TaskContext taskContext = TaskContext.get();
        taskContext.setSystem("JOBS");
        taskContext.setSubSystem(getName());
        taskContext.setJob("kernel");
    }

    protected void checkPermissions() {
        UserInfo currentUser = UserContext.getCurrentUser();
        getRequiredPermissions().forEach(currentUser::assertPermission);
    }

    protected Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider,
                                                        boolean enforceRequiredParameters,
                                                        Consumer<HandledException> errorConsumer) {
        Map<String, String> context = new HashMap<>();
        for (Parameter<?, ?> parameter : getParameters()) {
            try {
                String value = parameter.checkAndTransform(parameterProvider.apply(parameter.getName()));
                context.put(parameter.getName(), value);
                if (enforceRequiredParameters && Strings.isEmpty(value) && parameter.isRequired()) {
                    errorConsumer.accept(Exceptions.createHandled()
                                                   .withNLSKey("Parameter.required")
                                                   .set("name", parameter.getTitle())
                                                   .handle());
                }
            } catch (HandledException e) {
                errorConsumer.accept(e);
            }
        }

        return context;
    }
}
