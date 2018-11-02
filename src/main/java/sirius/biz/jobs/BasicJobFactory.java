/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.params.Parameter;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        return NLS.getIfExists(getClass().getSimpleName() + ".label", null)
                  .orElse(getClass().getSimpleName());
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
    public List<Parameter<?>> getParameters() {
        List<Parameter<?>> result = new ArrayList<>();
        collectParameters(result::add);
        return result;
    }

    protected abstract void collectParameters(Consumer<Parameter<?>> parameterCollector);

    @Override
    public String execute(Function<String, Value> parameterProvider) {
        JSONObject context = new JSONObject();
        for (Parameter<?> parameter : getParameters()) {
            context.put(parameter.getName(), parameter.checkAndTransform(parameterProvider.apply(parameter.getName())));
        }

        return executeWithContext(context);
    }

    protected abstract String executeWithContext(JSONObject context);
}
