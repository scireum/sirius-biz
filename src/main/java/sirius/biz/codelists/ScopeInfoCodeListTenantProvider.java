/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.tenants.Tenants;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;
import sirius.web.security.ScopeInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Register
public class ScopeInfoCodeListTenantProvider implements Transformer<ScopeInfo, CodeListTenantProvider> {

    @Part
    private Tenants<?, ?, ?> tenants;

    @Override
    public Class<ScopeInfo> getSourceClass() {
        return ScopeInfo.class;
    }

    @Override
    public Class<CodeListTenantProvider> getTargetClass() {
        return CodeListTenantProvider.class;
    }

    @Nullable
    @Override
    public CodeListTenantProvider make(@Nonnull ScopeInfo scopeInfo) {
        if (scopeInfo == ScopeInfo.DEFAULT_SCOPE) {
            return () -> tenants.getCurrentTenant().orElse(null);
        } else {
            return () -> null;
        }
    }
}
