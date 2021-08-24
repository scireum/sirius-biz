/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Resolves {@link sirius.biz.tenants.Tenant tenants} utilizing the cache of the
 * {@link sirius.biz.tenants.TenantUserManager}.
 */
@Register
public class TenantResolver implements SmartValueResolver<Tenant<?>> {

    public static final String TYPE_TENANT = "tenant";

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Tenant<?>> tryResolve(String type, String payload) {
        if (tenants != null && TYPE_TENANT.equals(type)) {
            return (Optional<Tenant<?>>) tenants.fetchCachedTenant(payload);
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
