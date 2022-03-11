/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.infos.JobInfo;
import sirius.biz.jobs.infos.JobInfoCollector;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.http.QueryString;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
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
     * Contains the name of the key used to pass along the {@link Tenant admin tenant} ID,
     * when a spying user starts an {@link #shouldExecuteInAdminTenant() admin-only} job.
     */
    public static final String ADMIN_TENANT_ID_KEY = "adminTenantId";

    /**
     * Contains the name of the key used to pass along the {@link Tenant admin tenant} name,
     * when a spying user starts an {@link #shouldExecuteInAdminTenant() admin-only} job.
     */
    public static final String ADMIN_TENANT_NAME_KEY = "adminTenantName";

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
        collector.addWell(getDetailDescription());
    }

    @Override
    public List<String> getRequiredPermissions() {
        return Arrays.stream(getClass().getAnnotationsByType(Permission.class))
                     .map(Permission::value)
                     .collect(Collectors.toList());
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

        if (!UserContext.getCurrentUser().hasPermissions(getRequiredPermissions().toArray(EMPTY_STRING_ARRAY))) {
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
            sb.append(Strings.urlEncode(NLS.toMachineString(setting.getValue())));
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
        checkPermissions();
        setupTaskContext();

        AtomicBoolean submit = new AtomicBoolean(request.isSafePOST() && !request.get(PARAM_UPDATE_ONLY).asBoolean());
        Map<String, String> context = buildAndVerifyContext(request::get, submit.get(), error -> {
            UserContext.message(Message.error(error));
            submit.set(false);
        });

        if (submit.get()) {
            if (TenantController.isCurrentlySpying(request) && shouldExecuteInAdminTenant()) {
                Tenant<?> adminTenant = TenantController.determineCurrentTenant(request);
                context.put(ADMIN_TENANT_ID_KEY, adminTenant.getIdAsString());
                context.put(ADMIN_TENANT_NAME_KEY, adminTenant.getTenantData().getName());
            }
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
        checkPermissions();
        setupTaskContext();
        Map<String, String> context = buildAndVerifyContext(parameterProvider, true, error -> {
            throw error;
        });

        return executeInBackground(context);
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

    /**
     * Enforces the permissions specified by this job.
     * <p>
     * You cannot override this method, because it should behave consistently with {@link #getRequiredPermissions()}.
     * Please add all required permissions there.
     */
    protected final void checkPermissions() {
        UserInfo currentUser = UserContext.getCurrentUser();
        getRequiredPermissions().forEach(currentUser::assertPermission);
    }

    /**
     * Indicates if this job should only be executed in an admin tenant.
     * <p>
     * This helps prevent executing jobs in the wrong tenant, when the user that starts the job
     * {@link TenantController#isCurrentlySpying(WebContext) is currently spying} another tenant.
     *
     * @return <tt>true</tt> if the system administrator permission is required, <tt>false</tt> otherwise. Override this
     * and make it return <tt>true</tt> whenever it should be ensured that this job is only executed in an admin tenant
     */
    protected boolean shouldExecuteInAdminTenant() {
        return getRequiredPermissions().contains(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR);
    }

    @Override
    public Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider,
                                                     boolean enforceRequiredParameters,
                                                     Consumer<HandledException> errorConsumer) {
        Map<String, String> context = new HashMap<>();
        for (Parameter<?> parameter : getParameters()) {
            try {
                Value contextValue = parameterProvider.apply(parameter.getName());
                String value = parameter.checkAndTransform(contextValue);
                if (enforceRequiredParameters && Strings.isEmpty(value) && parameter.isRequired()) {
                    errorConsumer.accept(Exceptions.createHandled()
                                                   .withNLSKey("Parameter.required")
                                                   .set("name", parameter.getLabel())
                                                   .handle());
                } else {

                    context.put(parameter.getName(), value);
                }
            } catch (HandledException e) {
                errorConsumer.accept(e);
            }
        }

        return context;
    }
}
