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

/**
 * Provides a base implementation for all import handlers which mainly takes care of the convenience methods.
 *
 * @param <E> the generic type of entities being handled by this import handler.
 */
public abstract class BaseImportHandler<E extends BaseEntity<?>> implements ImportHandler<E> {

    @Part
    protected static Mixing mixing;

    protected EntityDescriptor descriptor;
    protected ImportContext context;

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected BaseImportHandler(Class<?> clazz, ImportContext context) {
        this.context = context;
        descriptor = mixing.getDescriptor(clazz);
    }

    /**
     * Loads the given list of mappings from the given data into a new instance of the entity type.
     *
     * @param data     the data to load values from
     * @param mappings the list of properties to load
     * @return a newly created and not yet persisted entity with values loaded from <tt>data</tt>
     */
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

    /**
     * Loads the given property into the given entity from the given data source.
     *
     * @param entity   the entity to fill
     * @param property the property to load
     * @param data     the data source to read the value from
     */
    protected void loadProperty(E entity, Property property, Context data) {
        if (!data.containsKey(property.getName())) {
            return;
        }

        property.parseValue(entity, data.getValue(property.getName()));
        ensureTenantMatch(entity, property);
    }

    /**
     * Ensures that if the entity itself is tenant aware and the property being loaded also, that the tenants match.
     *
     * @param entity   the entity to fill
     * @param property the property which has been loaded
     */
    protected void ensureTenantMatch(BaseEntity<?> entity, Property property) {
        if ((entity instanceof TenantAware) && (property instanceof BaseEntityRefProperty)) {
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
