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
import sirius.kernel.Sirius;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a base implementation for all import handlers which mainly takes care of the convenience methods.
 *
 * @param <E> the generic type of entities being handled by this import handler.
 */
public abstract class BaseImportHandler<E extends BaseEntity<?>> implements ImportHandler<E> {

    @Part
    protected static Mixing mixing;

    protected EntityDescriptor descriptor;
    protected ImporterContext context;

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected BaseImportHandler(Class<?> clazz, ImporterContext context) {
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
    protected E load(Context data, Mapping... mappings) {
        return load(data, newEntity(), mappings);
    }

    /**
     * Loads the given list of mappings from the given data into a given instance of the entity type.
     *
     * @param data     the data to load values from
     * @param entity   the entity to load the data into
     * @param mappings the list of properties to load
     * @return a newly created and not yet persisted entity with values loaded from <tt>data</tt>
     */
    protected E load(Context data, E entity, Mapping... mappings) {
        Arrays.stream(mappings).forEach(mapping -> loadMapping(entity, mapping, data));
        return entity;
    }

    /**
     * Loads the given mapping into the given entity from the given data source.
     *
     * @param entity  the entity to fill
     * @param mapping the mapping to load
     * @param data    the data source to read the value from
     */
    protected void loadMapping(E entity, Mapping mapping, Context data) {
        if (!data.containsKey(mapping.getName())) {
            return;
        }

        loadProperty(entity, descriptor.getProperty(mapping), data);
    }

    /**
     * Loads the given property into the given entity from the given data source.
     *
     * @param entity   the entity to fill
     * @param property the property to load
     * @param data     the data source to read the value from
     */
    protected void loadProperty(E entity, Property property, Context data) {
        parseProperty(entity, property, data.getValue(property.getName()), data);
        ensureTenantMatch(entity, property);
    }

    /**
     * Parses the value for the given property.
     * <p>
     * Overwrite this method for smart / complext properties - especially to load referenced entities.
     *
     * @param entity   the entity to fill
     * @param property the property to parse
     * @param value    the value to parse
     * @param data     the full context which is being imported
     */
    protected void parseProperty(E entity, Property property, Value value, Context data) {
        property.parseValue(entity, value);
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
    public E findAndLoad(Context data) {
        return load(data, tryFind(data).orElse(newEntity()));
    }

    @Override
    public E findOrLoadAndCreate(Context data) {
        return tryFind(data).orElse(createOrUpdateNow(load(data, newEntity())));
    }

    /**
     * Creates a new entity of the handled entity type.
     *
     * @return new entity instance
     */
    @SuppressWarnings("unchecked")
    protected E newEntity() {
        try {
            return (E) descriptor.getType().getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw Exceptions.handle()
                            .error(e)
                            .withSystemErrorMessage("Cannot create an instance of: %s", descriptor.getType().getName())
                            .handle();
        }
    }

    @Override
    public ImportDictionary getDictionary() {
        ImportDictionary dict = new ImportDictionary(field -> {
            Property property = descriptor.findProperty(field);
            if (property != null) {
                return property.getLabel();
            } else {
                return field;
            }
        });

        loadAliases(dict);

        return dict;
    }

    /**
     * Determines the aliases and writes them into the given dictionary.
     * <p>
     * This will consult the system configuration under <tt>importer.aliases.ENTITY</tt>.
     *
     * @param dict the import dictionary to fill.
     */
    protected void loadAliases(ImportDictionary dict) {
        Extension aliases = Sirius.getSettings()
                                  .getExtension("importer.aliases", descriptor.getType().getSimpleName().toLowerCase());
        descriptor.getProperties().stream().map(Property::getName).forEach(mapping -> {
            dict.withAlias(mapping, mapping);
            if (aliases.getConfig().hasPath(mapping)) {
                aliases.getStringList(mapping).forEach(alias -> dict.withAlias(mapping, alias));
            }
        });
    }

    /**
     * Determines all mappings which wear an {@link AutoImport} annotation.
     *
     * @return all mappings which wear the <tt>AutoImport</tt> annotation and are therefore to be considered to load
     * when updating or creating an entity.
     */
    protected List<Mapping> getAutoImportMappings() {
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> property.getAnnotation(AutoImport.class).isPresent())
                         .map(Property::getName)
                         .map(Mapping::named)
                         .collect(Collectors.toList());
    }
}
