/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Provides additional roles, which are given to the {@link sirius.web.security.UserInfo UserInfo} in the {@link TenantUserManager}.
 */
public interface AdditionalRolesProvider {

    /**
     * Adds additonal roles to the given {@link Set} of roles, based on the given {@link Tenant} and {@link UserAccount}.
     *
     * @param user           the {@link UserAccount} for which the roles should be calculated
     * @param tenant         the {@link Tenant} for which the roles should be calculated
     * @param isSystemTenant indicate wheter the tenant is the system tenant
     * @param roleConsumer   the consumer for the additonal roles
     */
    void addAdditionalRoles(UserAccount<?, ?> user,
                            Tenant<?> tenant,
                            boolean isSystemTenant,
                            Consumer<String> roleConsumer);
}
