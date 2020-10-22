/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.checks.DailyCheck;
import sirius.biz.tycho.academy.RecomputeOnboardingVideosCheck;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class OnboardVideosCheck /* extends RecomputeOnboardingVideosCheck<SQLUserAccount> {

    @Part
    private SQLTenants tenants;

    @Override
    protected boolean checkPermission(SQLUserAccount entity, String permission) {
        if (Strings.isEmpty(permission)) {
            return true;
        }

        if (entity.getUserAccountData().getPermissions().hasPermission(permission)) {
            return true;
        }

        return tenants.fetchCachedTenant(entity.getTenant())
                      .map(tenant -> tenant.hasPermission(permission))
                      .orElse(false);
    }

    @Override
    protected void determineAcademies(SQLUserAccount entity, Consumer<String> academyConsumer) {
        academyConsumer.accept("test");
    }

    @Override
    protected void execute(SQLUserAccount entity) {
        //TODO ensure onboarding-flag
        super.execute(entity);
    }

    @Override
    public Class<SQLUserAccount> getType() {
        return SQLUserAccount.class;
    }
}
*/ {}
