/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.tycho.academy.RecomputeOnboardingVideosCheck;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.web.controller.SubScope;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;

public abstract class UserAccountVideosCheck<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends RecomputeOnboardingVideosCheck<U> {

    @Part
    @Nullable
    protected Tenants<I, T, U> tenants;

    @ConfigValue("tycho.onboarding.tenants-academies")
    private static List<String> tenantAcademies;

    @Override
    protected boolean checkPermission(U entity, String permission) {
        if (Strings.isEmpty(permission)) {
            return true;
        }

        if (entity.getUserAccountData().getPermissions().hasPermission(permission)) {
            return true;
        }

        if (tenants == null) {
            return false;
        }

        return tenants.fetchCachedTenant(entity.getTenant())
                      .map(tenant -> tenant.hasPermission(permission))
                      .orElse(false);
    }

    @Override
    protected void determineAcademies(U entity, Consumer<String> academyConsumer) {
        tenantAcademies.forEach(academyConsumer);
    }

    @Override
    public boolean isEnabled() {
        if (!super.isEnabled()) {
            return false;
        }
        if (tenants == null) {
            return false;
        }

        return !(tenantAcademies == null || tenantAcademies.isEmpty());
    }

    public static boolean isTenantOnboardingEnabled() {
        return onboardingEngine != null && !(tenantAcademies == null || tenantAcademies.isEmpty());
    }

    @Override
    protected void execute(U entity) {
        // Ignore locked / inactive users...
        if (entity.getUserAccountData().getLogin().isAccountLocked()) {
            return;
        }

        // Ignore API only users...
        if (!entity.getUserAccountData().getSubScopes().isEmpty() && !entity.getUserAccountData()
                                                                            .getSubScopes()
                                                                            .contains(SubScope.SUB_SCOPE_UI)) {
            return;
        }

        super.execute(entity);
    }
}
