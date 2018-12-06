/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.protocol.Journaled;
import sirius.biz.protocol.Traced;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;

/**
 * Represents a tenant using the system.
 * <p>
 * Helps to support multi tenancy for SaaS platforms.
 */
public interface Tenant<I> extends Traced, Journaled {

    Mapping PARENT = Mapping.named("parent");

    BaseEntityRef<I, ? extends Tenant<I>> getParent();

    Mapping TENANT_DATA = Mapping.named("tenantData");

    TenantData getTenantData();

    String getIdAsString();

    String getUniqueName();

    int getVersion();

    boolean isNew();
}
