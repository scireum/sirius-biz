/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Permits to select a {@link Tenant} as parameter.
 */
public class TenantParameter extends ParameterBuilder<Tenant<?>, TenantParameter> {

    @Part
    @Nullable
    private static Tenants<?, ?, ?> tenants;

    @Part
    @Nullable
    private static TenantController<?, ?, ?> tenantController;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    public TenantParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name and a default label.
     *
     * @param name the name of the parameter
     */
    public TenantParameter(String name) {
        this(name, NLS.get("Model.tenant"));
    }

    /**
     * Creates a new parameter with the default name and label.
     */
    public TenantParameter() {
        this("tenant");
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/tenant.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return resolveFromString(input).map(Tenant::getIdAsString).orElse(null);
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> Map.of("value", value.getIdAsString(), "text", value.toString()));
    }

    @Override
    protected Optional<Tenant<?>> resolveFromString(Value input) {
        Tenant<?> tenant =
                tenantController.resolveAccessibleTenant(input.asString(), tenants.getRequiredTenant()).orElse(null);
        return Optional.ofNullable(tenant);
    }
}
