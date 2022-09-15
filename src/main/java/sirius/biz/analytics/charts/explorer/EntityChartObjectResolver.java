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

    @Override
    public String fetchIdentifier(E object) {
        return object.getIdAsString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<E> resolve(String identifier) {
        return mixing.getDescriptor(getTargetType()).getMapper().find((Class<E>)getTargetType(), identifier).map(entity -> {
            if (entity instanceof TenantAware tenantAware) {
                tenants.assertTenant(tenantAware);
            }
            return entity;
        });
    }
}
