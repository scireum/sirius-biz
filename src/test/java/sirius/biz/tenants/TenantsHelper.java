/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.LoginData;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.biz.tenants.jdbc.SQLTenantUserManager;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.web.http.TestRequest;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.time.Duration;
import java.util.List;

/**
 * Provides a test scenario with a tenant and an associated user account.
 */
public class TenantsHelper {

    @Part
    private static OMA oma;

    @ConfigValue("security.roles")
    private static List<String> roles;

    @ConfigValue("security.tenantPermissions")
    private static List<String> features;

    @Part
    private static SQLTenants tenants;

    private static SQLTenant testTenant;
    private static SQLUserAccount testUser;

    private TenantsHelper() {
    }

    /**
     * Installs the test tenant and user into the local {@link UserContext}.
     */
    public static void installTestTenant() {
        if (tenants.getCurrentTenant().isPresent()) {
            return;
        }

        UserContext.get()
                   .setCurrentUser(((SQLTenantUserManager) UserContext.get().getUserManager()).asUser(getTestUser(),
                                                                                                      null,
                                                                                                      null));
    }

    private static void setupTestTenant() {
        oma.getReadyFuture().await(Duration.ofSeconds(60));

        testTenant = oma.select(SQLTenant.class).eq(Tenant.TENANT_DATA.inner(TenantData.NAME), "Test").queryFirst();

        if (testTenant == null) {
            testTenant = new SQLTenant();
            testTenant.getTenantData().setName("Test");
            testTenant.getTenantData().getPackageData().getAdditionalPermissions().addAll(features);
            oma.update(testTenant);
        }
    }

    private static void setupTestUser() {
        oma.getReadyFuture().await(Duration.ofSeconds(60));

        testUser = oma.select(SQLUserAccount.class)
                      .eq(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME), "test")
                      .queryFirst();
        if (testUser == null) {
            testUser = new SQLUserAccount();
            testUser.getTenant().setValue(getTestTenant());
            testUser.getUserAccountData().getLogin().setUsername("test");
            testUser.getUserAccountData().getLogin().setCleartextPassword("test");
            testUser.getUserAccountData().getPermissions().getPermissions().addAll(roles);
            testUser.getUserAccountData().setEmail("test@test.test");
            oma.update(testUser);
        }
    }

    /**
     * Installs the test tenant and user into the given request.
     *
     * @param request the request to perform as test user and tenant
     */
    public static void installBackendUser(TestRequest request) {
        SQLTenantUserManager tenantUserManager = (SQLTenantUserManager) UserContext.get().getUserManager();
        UserInfo userInfo = tenantUserManager.findUserByUserId(getTestUser().getUniqueName());

        tenantUserManager.updateLoginCookie(request, userInfo, true);
    }

    public static void clearCurrentUser() {
        UserContext.get().setCurrentUser(null);
    }

    /**
     * Returns a sample {@link Tenant} for testing or creates one if doesn't exist.
     * Do not use this tenant for test in which you delete or change him since this might have effects on other tests.
     *
     * @return Tenant for tests
     */
    public static SQLTenant getTestTenant() {
        if (testTenant == null) {
            setupTestTenant();
        }
        return testTenant;
    }

    /**
     * Returns a sample {@link UserAccount} for testing or creates one if doesn't exist.
     * Do not use this UserAccount for test in which you delete or change him since this might have effects on other
     * tests.
     *
     * @return UserAccount for tests
     */
    public static SQLUserAccount getTestUser() {
        if (testUser == null) {
            setupTestUser();
        }
        return testUser;
    }
}
