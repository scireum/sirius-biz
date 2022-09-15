/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics;

import sirius.biz.analytics.charts.explorer.ChartObjectResolver;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Resolves accessible {@link Tenant tenants}.
 */
@Register
public class TenantResolver implements ChartObjectResolver<Tenant<?>> {

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    @Part
    @Nullable
    private TenantController<?, ?, ?> tenantController;

    @Override
    public Class<? super Tenant<?>> getTargetType() {
        return Tenant.class;
    }

    @Override
    public String fetchIdentifier(Tenant<?> object) {
        return object.getIdAsString();
    }

    @SuppressWarnings({"unchecked", "RedundantCast"})
    @Override
    public Optional<Tenant<?>> resolve(String identifier) {
        Tenant<?> currentTenant = tenants.getRequiredTenant();
        if (Strings.areEqual(currentTenant.getIdAsString(), identifier) || !UserContext.getCurrentUser()
                                                                                       .hasPermission(TenantUserManager.PERMISSION_SELECT_TENANT)) {
            return (Optional<Tenant<?>>) (Optional<?>) Optional.of(currentTenant);
        } else {
            return (Optional<Tenant<?>>) tenantController.resolveAccessibleTenant(identifier, currentTenant);
        }
    }

    @Override
    public String getAutocompleteUri() {
        return "/tenants/autocomplete";
    }

    @Nonnull
    @Override
    public String getName() {
        return "tenant";
    }
}
