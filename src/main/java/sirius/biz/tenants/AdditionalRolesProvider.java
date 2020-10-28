/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import java.util.function.Consumer;

/**
 * Provides additional roles, which are given to the {@link sirius.web.security.UserInfo UserInfo} in the {@link TenantUserManager}.
 */
public interface AdditionalRolesProvider {

    /**
     * Adds additonal roles to the given roleConsumer, based on the given {@link UserAccount}.
     *
     * @param user         the {@link UserAccount} for which the roles should be calculated
     * @param roleConsumer the consumer for the additonal roles
     */
    void addAdditionalRoles(UserAccount<?, ?> user, Consumer<String> roleConsumer);

    /**
     * Adds additional roles / permissions / flag specifically to {@link Tenant#hasPermission(String)}.
     * <p>
     * Note that permissions added here, still have to be reported by {@link #addAdditionalRoles(UserAccount, Consumer)}
     * in order to be present for a user.
     * <p>
     * The main reason to implement this method is to toggle some flags which are used in role to permission
     * maps which e.g. decide which roles are offered for a user (based on the capabilities of its tenant).
     *
     * @param tenant       the tenant to compute roles / permissions / flags for
     * @param roleConsumer the consumer to be provided with additional roles / permissions / flags
     */
    default void addAdditionalTenantRoles(Tenant<?> tenant, Consumer<String> roleConsumer) {
        // The default implementation does intentionally nothing...
    }
}
