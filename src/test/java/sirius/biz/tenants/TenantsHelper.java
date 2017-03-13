/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.LoginData;
import sirius.db.mixing.OMA;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.web.http.TestRequest;
import sirius.web.security.UserContext;

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
    private static Tenants tenants;

    private static Tenant testTenant;
    private static UserAccount testUser;

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
                   .setCurrentUser(((TenantUserManager) UserContext.get().getUserManager()).asUser(getTestUser(), null));
    }

    private static void setupTestTenant() {
        oma.getReadyFuture().await(Duration.ofSeconds(60));

        testTenant = oma.select(Tenant.class).eq(Tenant.NAME, "Test").queryFirst();
        if (testTenant == null) {
            testTenant = new Tenant();
            testTenant.setName("Test");
            testTenant.getPermissions().getPermissions().addAll(features);
            oma.update(testTenant);
        }
    }

    private static void setupTestUser() {
        oma.getReadyFuture().await(Duration.ofSeconds(60));

        testUser = oma.select(UserAccount.class).eq(UserAccount.LOGIN.inner(LoginData.USERNAME), "test").queryFirst();
        if (testUser == null) {
            testUser = new UserAccount();
            testUser.getTenant().setValue(getTestTenant());
            testUser.getLogin().setUsername("test");
            testUser.getLogin().setCleartextPassword("test");
            testUser.getPermissions().getPermissions().addAll(roles);
            testUser.setEmail("test@test.test");
            oma.update(testUser);
        }
    }

    /**
     * Installs the test tenant and user into the given request.
     *
     * @param request the request to perform as test user and tenant
     */
    public static void installBackendUser(TestRequest request) {
        request.setSessionValue("default-tenant-id", getTestTenant().getId());
        request.setSessionValue("default-tenant-name", getTestTenant().getName());
        request.setSessionValue("default-user-id", getTestUser().getUniqueName());
        request.setSessionValue("default-user-name", getTestUser().getEmail());
        request.setSessionValue("default-user-email", getTestUser().getEmail());
        request.setSessionValue("default-user-lang", "de");
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
    public static Tenant getTestTenant() {
        if (testTenant != null) {
            return testTenant;
        }
        setupTestTenant();
        return testTenant;
    }

    /**
     * Returns a sample {@link UserAccount} for testing or creates one if doesn't exist.
     * Do not use this UserAccount for test in which you delete or change him since this might have effects on other
     * tests.
     *
     * @return UserAccount for tests
     */
    public static UserAccount getTestUser() {
        if (testUser != null) {
            return testUser;
        }
        setupTestUser();
        return testUser;
    }
}
