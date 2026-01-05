/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyValidator;
import sirius.db.mixing.properties.BaseEntityRefProperty;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Ensures that referenced entities belong to the same tenant as the referencing entity.
 * <p>
 * This can be attached to {@link BaseEntityRefProperty} instances which reference {@link TenantAware}
 * entities inside a {@link TenantAware} entity.
 */
@Register
public class EnforceSameTenantValidator implements PropertyValidator {

    @Override
    public void validate(BaseEntity<?> entity, Property property, Object value, Consumer<String> validationConsumer) {
        // Nothing to do here ...
    }

    @Override
    public void beforeSave(BaseEntity<?> entity, Property property, Object value) {
        if ((entity instanceof TenantAware tenantAwareEntity)
            && property instanceof BaseEntityRefProperty<?, ?, ?> refProperty) {
            Object loadedEntity = property.getValue(entity);
            if (loadedEntity != null && TenantAware.class.isAssignableFrom(refProperty.getReferencedType())) {
                tenantAwareEntity.assertSameTenant(property::getLabel,
                                                   (TenantAware) refProperty.getEntityRef(tenantAwareEntity)
                                                                            .fetchCachedValueFromSecondary());
            }
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "enforce-same-tenant-validator";
    }
}
