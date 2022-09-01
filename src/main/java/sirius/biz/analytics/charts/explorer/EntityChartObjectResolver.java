/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.tenants.Tenants;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.std.Part;

import java.util.Optional;

/**
 * Provides a basic resolve implementation for {@link BaseEntity entities}.
 * <p>
 * Note that this doesn't perform any access checks other than for the proper tenant if the entity happens to
 * be {@link TenantAware}.
 *
 * @param <E> the type of entities being resolved
 */
public abstract class EntityChartObjectResolver<E extends BaseEntity<?>> implements ChartObjectResolver<E> {

    @Part
    protected Mixing mixing;

    @Part
    protected Tenants<?, ?, ?> tenants;

    /**
     * Specifies the type of entities being resolved.
     *
     * @return the type of entities being resolved by this resolver
     */
    protected abstract Class<E> getEntityType();

    @Override
    public String fetchIdentifier(E object) {
        return object.getIdAsString();
    }

    @Override
    public Optional<E> resolve(String identifier) {
        return mixing.getDescriptor(getEntityType()).getMapper().find(getEntityType(), identifier).map(entity -> {
            if (entity instanceof TenantAware tenantAware) {
                tenants.assertTenant(tenantAware);
            }
            return entity;
        });
    }
}
