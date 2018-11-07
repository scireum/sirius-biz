/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.params.Parameter;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public void runInUI(WebContext request) {
        if (request.isSafePOST()) {
            try {
                callInUI(request);
                return;
            } catch (Exception e) {
                UserContext.handle(e);
            }
        }
        Map<String, String> context = new HashMap<>();
        try {
            context = buildAndVerifyContext(request::get);
        } catch (Exception e) {
            UserContext.handle(e);
        }
        request.respondWith().template("/templates/jobs/job.html.pasta", this, context);
    }

    protected abstract void callInUI(WebContext request);

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

    protected Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider) {
        Map<String, String> context = new HashMap<>();
        for (Parameter<?, ?> parameter : getParameters()) {
            context.put(parameter.getName(), parameter.checkAndTransform(parameterProvider.apply(parameter.getName())));
        }

        return context;
    }
}
