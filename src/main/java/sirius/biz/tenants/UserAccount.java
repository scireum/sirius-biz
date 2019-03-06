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
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real enttiy.")
public interface UserAccount<I, T extends BaseEntity<I> & Tenant<I>>
        extends Transformable, MessageProvider, Traced, Journaled, TenantAware {

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
     * Returns an unique name of this entity.
     * <p>
     * This unique string representation of this entity is made up of its type along with its id.
     *
     * @return an unique representation of this entity or an empty string if the entity was not written to the
     * database yet
     */
    String getUniqueName();

    /**
     * Returns the version of the entity.
     *
     * @return the version of the entity
     */
    int getVersion();

    /**
     * Determines if the entity is new (not yet written to the database).
     *
     * @return <tt>true</tt> if the entity has not been written to the database yes, <tt>false</tt> otherwise
     */
    boolean isNew();

    /**
     * Returns a string representation of the entity ID.
     * <p>
     * If the entity is new, "new" will be returned.
     *
     * @return the entity ID as string or "new" if the entity {@link #isNew()}.
     */
    String getIdAsString();

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
