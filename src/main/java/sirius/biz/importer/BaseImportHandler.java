/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.BaseEntityRefProperty;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.util.Arrays;

public abstract class BaseImportHandler<E extends BaseEntity<?>> implements ImportHandler<E> {

    @Part
    protected static Mixing mixing;

    protected EntityDescriptor descriptor;
    protected ImportContext context;

    protected BaseImportHandler(ImportContext context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    protected E load(Context data, Mapping... mappings) {
        try {
            E entity = (E) descriptor.getType().newInstance();
            Arrays.stream(mappings)
                  .map(descriptor::getProperty)
                  .forEach(property -> loadProperty(entity, property, data));
            return entity;
        } catch (InstantiationException | IllegalAccessException e) {
            throw Exceptions.handle()
                            .error(e)
                            .withSystemErrorMessage("Cannot create an instance of: %s", descriptor.getType().getName())
                            .handle();
        }
    }

    protected void loadProperty(E entity, Property property, Context data) {
        if (!data.containsKey(property.getName())) {
            return;
        }

        property.parseValue(entity, data.getValue(property.getName()));
    }

    private void ensureTenantMatch(BaseEntity<?> entity, Property property) {
        if ((entity instanceof TenantAware) && property instanceof BaseEntityRefProperty) {
            Object loadedEntity = property.getValue(entity);
            if (loadedEntity instanceof TenantAware) {
                ((TenantAware) entity).assertSameTenant(property::getLabel, (TenantAware) loadedEntity);
            }
        }
    }

    @Override
    public E findOrFail(Context data) {
        return tryFind(data).orElseThrow(() -> Exceptions.createHandled()
                                                         .withSystemErrorMessage(
                                                                 "Cannot find an instance for: %s of type %s",
                                                                 data,
                                                                 descriptor.getType().getName())
                                                         .handle());
    }

    @Override
    public E findOrLoad(Context data) {
        return tryFind(data).orElse(load(data));
    }

    @Override
    public E findOrLoadAndCreate(Context data) {
        return tryFind(data).orElse(createOrUpdateNow(load(data)));
    }
}
