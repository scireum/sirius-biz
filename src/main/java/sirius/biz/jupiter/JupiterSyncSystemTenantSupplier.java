/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.di.std.AutoRegister;

/**
 * Provides the system tenant for Jupiter when the {@linkplain sirius.biz.tenants.Tenants#FRAMEWORK_TENANTS tenants framework}
 * is not available. In this case, products need to implement this interface and {@linkplain sirius.kernel.di.std.Register register}
 * the implementation.
 */
@AutoRegister
public interface JupiterSyncSystemTenantSupplier {

    /**
     * Returns the identifier of the system tenant.
     *
     * @return the identifier of the system tenant
     */
    String getSystemTenantId();

    /**
     * Returns the name of the system tenant.
     *
     * @return the name of the system tenant
     */
    String getSystemTenantName();
}
