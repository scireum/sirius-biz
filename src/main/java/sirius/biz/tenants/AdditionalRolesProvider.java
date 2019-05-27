/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Provides additional roles, which are given to the {@link sirius.web.security.UserInfo UserInfo} in the {@link TenantUserManager}.
 */
public interface AdditionalRolesProvider {

    /**
     * Adds additonal roles to the given {@link Set} of roles, based on the given {@link Tenant} and {@link UserAccount}.
     *
     * @param user   the {@link UserAccount} for which the roles should be calculated
     * @param tenant the {@link Tenant} for which the roles should be calculated
     * @param roles  the roles calculated by the framework
     * @return the {@link Set} of roles, which will be given to the {@link sirius.web.security.UserInfo UserInfo} as roles.
     */
    @Nonnull
    Set<String> addAdditionalRoles(UserAccount<?, ?> user, Tenant<?> tenant, Set<String> roles);
}
