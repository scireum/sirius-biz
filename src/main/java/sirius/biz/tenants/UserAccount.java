/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.biz.isenguard.RateLimitedEntity;
import sirius.biz.protocol.Journaled;
import sirius.biz.protocol.Traced;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.transformers.Transformable;
import sirius.web.security.MessageProvider;

/**
 * Provides the database independent interface for describing a user account which uses the system.
 * <p>
 * Note that all fields are represented via {@link UserAccountData}.
 *
 * @param <I> the type used to represent database IDs
 * @param <T> specifies the effective entity type used to represent Tenants
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real entity.")
public interface UserAccount<I, T extends BaseEntity<I> & Tenant<I>>
        extends Transformable, MessageProvider, Traced, Journaled, TenantAware, RateLimitedEntity, PerformanceFlagged {

    /**
     * Defines the mapping which is used for the user account data composite.
     */
    Mapping USER_ACCOUNT_DATA = Mapping.named("userAccountData");

    /**
     * Returns the user account data composite.
     *
     * @return the composite holding all fields which describe a user account
     */
    UserAccountData getUserAccountData();

    /**
     * Sets the ID of the user account.
     * <p>
     * This method must be used very carefully. For normal operations, this method should ne be used at all
     * as the database ID is managed by the framework.
     *
     * @param id the id of the entity in the database
     */
    void setId(I id);

    @Override
    BaseEntityRef<I, T> getTenant();
}
