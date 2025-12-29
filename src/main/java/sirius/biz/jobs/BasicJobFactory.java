/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.jobs.infos.JobInfo;
import sirius.biz.jobs.infos.JobInfoCollector;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.http.QueryString;
import sirius.web.http.WebContext;
import sirius.web.security.Permissions;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides a robust base implementation of {@link JobFactory} which performs all the heavy lifting.
 */
public abstract class BasicJobFactory implements JobFactory {

    /**
     * Contains the {@link TaskContext#setSystem(String) system string} for jobs.
     */
    public static final String SYSTEM_JOBS = "JOBS";

    /**
     * Represents a special parameter which can be set when submitting a set of job parameters to
     * {@link #startInteractively(WebContext)}. This will only digest the given parameters but not
     * actually try to start the job.
     * <p>
     * This is used to pass in the parameters stored in a job preset - this way all values are
     * rendered nicely.
     */
    private static final String PARAM_UPDATE_ONLY = "updateOnly";

    /**
     * Defines the permission needed for bypassing log limitation.
     *
     * @see #LIMIT_LOG_MESSAGES_PARAMETER
     */
    public static final String FEATURE_BYPASS_PROCESS_LOG_LIMITS = "feature-bypass-process-log-limits";

    /**
     * Declares a boolean parameter that determines if log messages with a set limit should actually be limited.
     *
     * @see sirius.biz.process.logs.ProcessLog#withLimitedMessageType(String, int)
     * @see sirius.biz.process.ProcessContext#log(ProcessLog)
     */
    public static final Parameter<Boolean> LIMIT_LOG_MESSAGES_PARAMETER =
            new BooleanParameter("limitLogMessages", "$BasicJobFactory.limitLogMessages").withDescription(
                    "$BasicJobFactory.limitLogMessages.help").withDefaultTrue().build();

    @Part
    @Nullable
    private JobPresets presets;

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

    @Nullable
    @Override
    public String getDetailDescription() {
        return NLS.getIfExists(getClass().getSimpleName() + ".detailDescription", null).orElse(null);
    }

    @Nullable
    @Override
    public String getHTMLDescription() {
        return null;
    }

    @Override
    public List<JobInfo> getJobInfos() {
        JobInfoCollector collector = new JobInfoCollector();
        collectJobInfos(collector);
        return collector.getInfos();
    }

    /**
     * Overwrite to provide additional documentation for a job.
     *
     * @param collector the collector used to supply additional info sections for a job
     */
    protected void collectJobInfos(JobInfoCollector collector) {
        collector.addCard(getDetailDescription());
    }

    @Override
    public boolean isAccessibleToCurrentUser() {
        return UserContext.getCurrentUser().hasPermissions(getRequiredPermissions().toArray(String[]::new));
    }

    /**
     * Fetches the permissions required by this job.
     * <p>
     * By default, we pick up all {@link sirius.web.security.Permission} annotations on the job class.
     *
     * @return a set of all required permissions for this job
     */
    protected Set<String> getRequiredPermissions() {
        return Permissions.computePermissionsFromAnnotations(getClass());
    }

    @Override
    public List<Parameter<?>> getParameters() {
        List<Parameter<?>> result = new ArrayList<>();
        collectParameters(result::add);
        return result;
    }

    @Override
    public boolean hasVisibleParameters(Map<String, String> context) {
        return getParameters().stream().anyMatch(parameter -> parameter.isVisible(context));
    }

    /**
     * Collects all parameters expected by the job.
     *
     * @param parameterCollector the collector to be supplied with the expected parameters
     */
    protected abstract void collectParameters(Consumer<Parameter<?>> parameterCollector);

    /**
     * Collects all {@link VirtualFile files} that should be marked read-only at job submission.
     *
     * @param context       the parameters wrapped as context
     * @param fileCollector the collector to be supplied with the list of files
     */
    protected void collectFilesToLock(Map<String, String> context, Consumer<VirtualFile> fileCollector) {
        // Nothing to do by default
    }

    protected void lockFiles(Map<String, String> context) {
        List<VirtualFile> filesToLock = new ArrayList<>();
        collectFilesToLock(context, filesToLock::add);
        filesToLock.forEach(virtualFile -> virtualFile.updateReadOnlyFlag(true));
    }

    @Nullable
    @Override
    public String generatePresetUrl(String uri, Object targetObject) {
        if (uri == null) {
            return null;
        }

        QueryString queryString = new QueryString(uri);

        if (!hasPresetFor(queryString, targetObject)) {
            return null;
        }

        if (!canStartInteractive()) {
            return null;
        }

        if (!isAccessibleToCurrentUser()) {
            return null;
        }

        Map<String, Object> preset = new HashMap<>();
        computePresetFor(queryString, targetObject, preset);
        StringBuilder sb = new StringBuilder("/job/");
        sb.append(getName());
        Monoflop mf = Monoflop.create();
        for (Map.Entry<String, Object> setting : preset.entrySet()) {
            sb.append(mf.firstCall() ? "?" : "&");
            sb.append(setting.getKey());
            sb.append("=");
            sb.append(Urls.encode(NLS.toMachineString(setting.getValue())));
        }
        return sb.toString();
    }

    /**
     * Determines if this job can compute a preset of parameters for the given query string.
     * <p>
     * If this method is overwritten to return <tt>true</tt> {@link #computePresetFor(QueryString, Object, Map)} also should be
     * overwritten.
     *
     * @param queryString  the query string to derive a preset from
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @return <tt>true</tt> if the given query string can be used to compute a preset of parameters from
     */
    protected boolean hasPresetFor(@Nonnull QueryString queryString, @Nullable Object targetObject) {
        return false;
    }

    /**
     * Computes the preset parameter values from the given query string.
     *
     * @param queryString  the query string to derive a preset from
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @param preset       used to be supplied with the computed parameters which are then encoded into the preset URL
     */
    protected void computePresetFor(@Nonnull QueryString queryString,
                                    @Nullable Object targetObject,
                                    Map<String, Object> preset) {
        // NOOP by default
    }

    @Override
    public void startInteractively(WebContext request) {
        enforceAccessibility();
        setupTaskContext();

        AtomicBoolean submit = new AtomicBoolean(request.isSafePOST() && !request.get(PARAM_UPDATE_ONLY).asBoolean());
        Map<String, String> context = buildAndVerifyContext(request::get, submit.get(), (param, error) -> {
            UserContext.message(Message.error(error));
            submit.set(false);
        });

        if (submit.get()) {
            executeInteractive(request, context);
            return;
        }

        request.respondWith().template("/templates/biz/jobs/job.html.pasta", this, context, presets);
    }

    /**
     * Executes the job while running in the UI.
     * <p>
     * Note that this sill executes in the web server thread.
     *
     * @param request the request to fulfill
     * @param context the parameters wrapped as context
     */
    protected abstract void executeInteractive(WebContext request, Map<String, String> context);

    @Override
    public String startInBackground(Function<String, Value> parameterProvider) {
        enforceAccessibility();
        setupTaskContext();

        return executeInBackground(buildAndVerifyContext(parameterProvider));
    }

    /**
     * Builds and verifies the context for this job execution based on the given parameter provider.
     *
     * @param parameterProvider provides the values for the parameters of this job
     * @return a map containing the parameter names as keys and the transformed values as values
     * @throws HandledException if any provided parameter value is invalid or required but missing.
     * @implNote exceptions are logged extra as they are most likely unlogged handled exceptions which would otherwise
     * lead to silent failures of job presets and scheduled jobs .
     */
    private Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider) {
        return buildAndVerifyContext(parameterProvider, true, (parameter, handledException) -> {
            Exceptions.handle().to(Log.BACKGROUND).withSystemErrorMessage("""
                                                                                  Starting background job failed for erroneous parameter:
                                                                                   - Tenant: %s
                                                                                   - Job: %s
                                                                                   - Parameter: %s
                                                                                   - Message: %s
                                                                                  """,
                                                                          UserContext.getCurrentUser().getTenantId(),
                                                                          getName(),
                                                                          parameter.getName(),
                                                                          handledException.getMessage()).handle();

            throw handledException;
        });
    }

    /**
     * Executes the job in the background.
     *
     * @param context the parameters wrapped as context
     * @return the id of the {@link sirius.biz.process.Process} which has been started to cover the execution or
     * <tt>null</tt> if no process was used.
     */
    @Nullable
    protected abstract String executeInBackground(Map<String, String> context);

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

    protected final void enforceAccessibility() {
        if (!isAccessibleToCurrentUser()) {
            throw Exceptions.createHandled().withDirectMessage(NLS.get("BasicJobFactory.unauthorized")).handle();
        }
    }

    @Override
    public Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider,
                                                     boolean enforceRequiredParameters,
                                                     BiConsumer<Parameter<?>, HandledException> errorConsumer) {
        Map<String, String> context = new HashMap<>();
        for (Parameter<?> parameter : getParameters()) {
            try {
                Value contextValue = parameterProvider.apply(parameter.getName());
                String value = parameter.checkAndTransform(contextValue);
                if (enforceRequiredParameters && Strings.isEmpty(value) && parameter.isRequired()) {
                    errorConsumer.accept(parameter,
                                         Exceptions.createHandled()
                                                   .withNLSKey("Parameter.required")
                                                   .set("name", parameter.getLabel())
                                                   .handle());
                } else {

                    context.put(parameter.getName(), value);
                }
            } catch (HandledException exception) {
                errorConsumer.accept(parameter, exception);
            }
        }

        return context;
    }

    @Override
    public JsonNode computeRequiredParameterUpdates(WebContext webContext) {
        Map<String, Exception> errorByParameter = new HashMap<>();
        Map<String, String> parameterContext = buildAndVerifyContext(webContext::get, false, (parameter, exception) -> {
            errorByParameter.put(parameter.getName(), exception);
        });
        return computeRequiredParameterUpdates(parameterContext, errorByParameter);
    }

    @Override
    public JsonNode computeRequiredParameterUpdates(Map<String, String> parameterContext) {
        return computeRequiredParameterUpdates(parameterContext, Collections.emptyMap());
    }

    private JsonNode computeRequiredParameterUpdates(Map<String, String> parameterContext,
                                                     Map<String, Exception> errorByParameter) {
        ObjectNode json = Json.createObject();
        getParameters().forEach(parameter -> {
            ObjectNode update = Json.createObject();
            update.put("visible", parameter.isVisible(parameterContext));
            update.put("clear", parameter.needsClear(parameterContext));
            parameter.updateValue(parameterContext).ifPresent(val -> update.putPOJO("updatedValue", val));
            Optional<Message> validation = parameter.validate(parameterContext);
            if (errorByParameter.containsKey(parameter.getName())) {
                validation = Optional.of(Message.error()
                                                .withTextMessage(errorByParameter.get(parameter.getName())
                                                                                 .getLocalizedMessage()));
            }
            validation.ifPresent(message -> {
                // pretty hacky to split css classes, but it works, and unless we change the Message type there is no
                // good way around it. The intention is, to reduce "alert-danger" to "danger", so we can use it in the
                // frontend in a more flexible way.
                String bootstrapStyle = message.getType().getCssClass().split("-")[1];
                update.set("validation",
                           Json.createObject()
                               .put("type", message.getType().name())
                               .put("style", bootstrapStyle)
                               .put("html", message.getHtml()));
            });
            json.set(parameter.getName(), update);
        });
        return json;
    }
}
