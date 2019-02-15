/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.tenants.Tenant;

import javax.annotation.Nullable;

/**
 * Can be used to specify which tenant to use for a given scope.
 * <p>
 * A {@link sirius.kernel.di.transformers.Transformer} for {@link sirius.web.security.ScopeInfo} has to be registered
 * so that a scope can specify which tenant to use when accessing code lists.
 * <p>
 * Note that the {@link sirius.web.security.ScopeInfo#DEFAULT_SCOPE} always uses the effective tenant of the
 * {@link sirius.biz.tenants.TenantUserManager}.
 */
public interface CodeListTenantProvider {

    /**
     * Returns the tenant to use.
     *
     * @return the tenant to use when accessing code lists
     */
    @Nullable
    Tenant<?> getCurrentTenant();
}
