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
import sirius.biz.tycho.academy.OnboardingParticipant;
import sirius.biz.web.TenantAware;
import sirius.biz.web.UserIconProvider;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Value;
import sirius.kernel.di.transformers.Transformable;
import sirius.web.security.MessageProvider;

import javax.annotation.Nullable;
import java.io.Serializable;

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
public interface UserAccount<I extends Serializable, T extends BaseEntity<I> & Tenant<I>>
        extends Transformable, MessageProvider, Traced, Journaled, TenantAware, RateLimitedEntity, PerformanceFlagged,
                UserIconProvider, OnboardingParticipant {

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

    /**
     * Reads the preference with the given name/key.
     *
     * @param key the name of the preference to read
     * @return the value stores for the given preference
     */
    Value readPreference(String key);

    /**
     * Updates/stores the preference with the given name/key.
     * <p>
     * This will directly update the underlying database and store the preferences. Note, that in order to keep
     * this list concise, prefer a <tt>null</tt> + default value for the "average case" which will not be stored
     * at all. So a key like "layout" should store <tt>"TABLE"</tt> / <tt>null</tt> in favor of "useCardLayout" with
     * <tt>true</tt> and <tt>false</tt> or <tt>"TABLE"</tt> and <tt>"CARDS"</tt> as enum values.
     *
     * @param key   the name of the preference to store
     * @param value the value to store. This should be either a <tt>String</tt>, <tt>boolean</tt> or <tt>int</tt>.
     *              Use <tt>null</tt> to remove the reference.
     */
    void updatePreference(String key, @Nullable Object value);
}
