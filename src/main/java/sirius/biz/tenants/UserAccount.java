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
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.web.security.MessageProvider;

public interface UserAccount<I, T extends BaseEntity<I> & Tenant<I>> extends MessageProvider, Traced, Journaled, TenantAware {

    Mapping TENANT = Mapping.named("tenant");

    BaseEntityRef<I, T> getTenant();

    Mapping USER_ACCOUNT_DATA = Mapping.named("userAccountData");

    UserAccountData getUserAccountData();

    String getUniqueName();

    int getVersion();

    boolean isNew();

    String getIdAsString();

    void setId(I id);
}
