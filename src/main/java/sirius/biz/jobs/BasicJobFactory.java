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

/**
 * Provides a robust base implementation of {@link JobFactory} which performs all the heavy lifting.
 */
public abstract class BasicJobFactory implements JobFactory {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * Contains the {@link TaskContext#setSystem(String) system string} for jobs.
     */
    public static final String SYSTEM_JOBS = "JOBS";

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

    /**
     * Collects all parameters expected by the job.
     *
     * @param parameterCollector the collector to be supplied with the expected parameters
     */
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

        if (!canStartInUI()) {
            return null;
        }

        if (!UserContext.getCurrentUser().hasPermissions(getRequiredPermissions().toArray(EMPTY_STRING_ARRAY))) {
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

    /**
     * Determines if this job can compute a preset of parameters for the given target.
     * <p>
     * If this method is overwritten to return <tt>true</tt> {@link #computePresetFor(Object, Map)} also must be
     * overwritten.
     *
     * @param targetObject the target object to check
     * @return <tt>true</tt> if the given object can be used to compute a preset of parameters from
     */
    protected boolean hasPresetFor(Object targetObject) {
        return false;
    }

    /**
     * Computes the parameter values from the given target object.
     *
     * @param targetObject the object to compute the parameters from
     * @param preset       used to be supplied with the computed parameters which are then encoded into the preset URL
     */
    protected void computePresetFor(Object targetObject, Map<String, Object> preset) {
        throw new UnsupportedOperationException();
    }

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

    /**
     * Executes the job while running in the UI.
     * <p>
     * Note that this sill executes in the web server thread.
     *
     * @param request the request to fulfill
     * @param context the parameters wrapped as context
     */
    protected abstract void executeInUI(WebContext request, Map<String, String> context);

    @Override
    public void startInCall(WebContext request, JSONStructuredOutput out) {
        checkPermissions();
        setupTaskContext();
        Map<String, String> context = buildAndVerifyContext(request::get, true, error -> {
            throw error;
        });

        executeInCall(out, context);
    }

    /**
     * Executes the job as response to a JSON call.
     * <p>
     * Note that this sill executes in the web server thread.
     *
     * @param out     the JSON response to generate
     * @param context the parameters wrapped as context
     */
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

    /**
     * Executes the job in the background.
     *
     * @param context the parameters wrapped as context
     */
    protected abstract void executeInBackground(Map<String, String> context);

    /**
     * Fills the {@link TaskContext} with appropriate values.
     * <p>
     * Note that the job itself may (or should) specify a {@link TaskContext#setJob(String) job name}.
     */
    protected void setupTaskContext() {
        TaskContext taskContext = TaskContext.get();
        taskContext.setSystem(SYSTEM_JOBS);
        taskContext.setSubSystem(getName());
        taskContext.setJob("kernel");
    }

    /**
     * Enforces the permissions sepcified by this job.
     */
    protected void checkPermissions() {
        UserInfo currentUser = UserContext.getCurrentUser();
        getRequiredPermissions().forEach(currentUser::assertPermission);
    }

    /**
     * Builds a context using values from the given <tt>parameterProvider</tt> and the
     * {@link #getParameters() parameters} sepcified by this job.
     *
     * @param parameterProvider         used to provide parameter values
     * @param enforceRequiredParameters determines if required parameters should be enforced
     * @param errorConsumer             will be supplied with detected errors
     * @return all provided parameters wrapped as context
     */
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
                                                   .set("name", parameter.getLabel())
                                                   .handle());
                }
            } catch (HandledException e) {
                errorConsumer.accept(e);
            }
        }

        return context;
    }
}
