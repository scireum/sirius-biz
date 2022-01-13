/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

/**
 * Permits to extend the {@link Tenant tenants} and {@link UserAccount users} created by {@link BaseTenantAutoSetup}.
 */
public interface TenantAutoSetupExtender {

    /**
     * Enhances the given tenant.
     *
     * @param tenant the tenant to enhance
     */
    void enhanceTenant(Tenant<?> tenant);

    /**
     * Enhances the given user.
     *
     * @param userAccount the user to enhance
     */
    void enhanceUser(UserAccount<?, ?> userAccount);
}
