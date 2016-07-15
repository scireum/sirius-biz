/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.LoginData;
import sirius.kernel.di.std.Part;
import sirius.db.mixing.OMA;
import sirius.web.security.UserContext;

public class TenantsHelper {

    @Part
    private static OMA oma;
    
    @Part
    private static Tenants tenants;

    private TenantsHelper() {
    }

    public static void installTestTenant() {
        if (tenants.getCurrentTenant().isPresent()) {
            return;
        }
        Tenant testTenant = oma.select(Tenant.class).eq(Tenant.NAME, "Test").queryFirst();
        if (testTenant == null) {
            testTenant = new Tenant();
            testTenant.setName("Test");
            oma.update(testTenant);
        }
        UserAccount user =
                oma.select(UserAccount.class).eq(UserAccount.LOGIN.inner(LoginData.USERNAME), "test").queryFirst();
        if (user == null) {
            user = new UserAccount();
            user.getTenant().setValue(testTenant);
            user.getLogin().setUsername("test");
            user.getLogin().setCleartextPassword("test");
            user.setEmail("test@test.test");
            oma.update(user);
        }
        UserContext.get().setCurrentUser(((TenantUserManager) UserContext.get().getUserManager()).asUser(user));
    }

    public static void clearCurrentUser() {
        UserContext.get().setCurrentUser(null);
    }
}
