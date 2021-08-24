/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

import java.util.Optional;

/**
 * Resolves known entities.
 * <p>
 * We expect the type to be the {@link EntityDescriptor#getName() type name} and the payload to be the entity id.
 */
@Register
public class EntityResolver implements SmartValueResolver<BaseEntity<?>> {

    @Part
    private Mixing mixing;

    @Override
    public Optional<BaseEntity<?>> tryResolve(String type, String payload) {
        return mixing.findDescriptor(type).flatMap(entityDescriptor -> tryFetchEntity(payload, entityDescriptor));
    }

    @SuppressWarnings("unchecked")
    private Optional<BaseEntity<?>> tryFetchEntity(String payload, EntityDescriptor entityDescriptor) {
        return entityDescriptor.getMapper().find((Class<BaseEntity<?>>) entityDescriptor.getType(), payload);
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
