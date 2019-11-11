/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.FieldDefinitionSupplier;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.FieldLookupCache;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.BaseEntityRefProperty;
import sirius.db.mixing.properties.StringListProperty;
import sirius.kernel.Sirius;
import sirius.kernel.commons.ComparableTuple;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides a base implementation for all import handlers which mainly takes care of the convenience methods.
 * <p>
 * Subclasses might most probably want to overwrite:
 * {@link #determineCacheKey(Context)} and {@link #getAutoImportMappings()}
 * as well as maybe {@link #parseProperty(BaseEntity, Property, Value, Context)}.
 *
 * @param <E> the generic type of entities being handled by this import handler.
 */
public abstract class BaseImportHandler<E extends BaseEntity<?>> implements ImportHandler<E> {

    @Part
    protected static Mixing mixing;

    @Part
    private static FieldLookupCache fieldLookupCache;

    @Parts(EntityImportHandlerExtender.class)
    protected static PartCollection<EntityImportHandlerExtender> extenders;

    protected EntityDescriptor descriptor;
    protected ImporterContext context;
    protected Mapping exportRepresentationMapping;
    protected Map<Mapping, BiConsumer<Context, Object>> loaders = new HashMap<>();
    private Extension aliases;

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected BaseImportHandler(Class<?> clazz, ImporterContext context) {
        this.context = context;
        this.descriptor = mixing.getDescriptor(clazz);
        this.exportRepresentationMapping = determineExportRepresentationMapping();
        this.aliases = Sirius.getSettings()
                             .getExtension("importer.aliases", descriptor.getType().getSimpleName().toLowerCase());

        for (EntityImportHandlerExtender extender : extenders) {
            extender.collectLoaders(this, descriptor, context, loaders::put);
        }
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

        if (entity instanceof TenantAware) {
            ((TenantAware) entity).setOrVerifyCurrentTenant();
        }

        return entity;
    }

    /**
     * Loads the given mapping into the given entity from the given data source.
     *
     * @param entity  the entity to fill
     * @param mapping the mapping to load
     * @param data    the data source to read the value from
     */
    @SuppressWarnings("unchecked")
    protected void loadMapping(E entity, Mapping mapping, Context data) {
        if (!data.containsKey(mapping.getName())) {
            return;
        }

        BiConsumer<Context, Object> loader =
                loaders.computeIfAbsent(mapping, m -> (d, e) -> loadProperty((E) e, descriptor.getProperty(m), d));
        loader.accept(data, entity);
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
     *
     * @param entity   the entity to fill
     * @param property the property to parse
     * @param value    the value to parse
     * @param data     the full context which is being imported
     */
    protected void parseProperty(E entity, Property property, Value value, Context data) {
        if (parseComplexProperty(entity, property, value, data)) {
            return;
        }

        property.parseValueFromImport(entity, value);
    }

    /**
     * Invoked to load complex properties (referenced entities).
     *
     * @param entity   the entity to fill
     * @param property the property to parse
     * @param value    the value to parse
     * @param data     the full context which is being imported
     * @return <tt>true</tt> if the load was handled, <tt>false</tt> if a regular load via
     * {@link Property#parseValueFromImport(Object, Value)} should be attempted.
     */
    protected boolean parseComplexProperty(E entity, Property property, Value value, Context data) {
        // No extra logic is performed by default.
        return false;
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

    @SuppressWarnings("unchecked")
    @Override
    public Optional<E> tryFindInCache(Context data) {
        Tuple<Class<?>, String> cacheKey = Tuple.create(descriptor.getType(), determineCacheKey(data));
        if (Strings.isFilled(cacheKey.getSecond())) {
            Object result = context.getLocalCache().getIfPresent(cacheKey);
            if (result != null) {
                return Optional.of((E) result);
            }
        }

        Optional<E> result = tryFind(data);
        if (result.isPresent() && Strings.isFilled(cacheKey)) {
            context.getLocalCache().put(cacheKey, result.get());
        }

        return result;
    }

    /**
     * Determines the cache key used by {@link #tryFindInCache(Context)} to find an instance in the cache.
     * <p>
     * Note that this isn't implemented by default and has to be overwritten by subclasses which want to support caching.
     *
     * @param data the data used to determine the cache key from
     * @return a unique string representation used for cache lookups or <tt>null</tt> to indicate that either caching
     * is completely disabled or that the example instance doesn't provide values in the relevant fields to support a
     * cache lookup.
     */
    protected String determineCacheKey(Context data) {
        throw new UnsupportedOperationException();
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
        return load(data, tryFind(data).orElseGet(this::newEntity));
    }

    @Override
    public E findOrLoadAndCreate(Context data) {
        return tryFind(data).orElseGet(() -> createOrUpdateNow(load(data, newEntity())));
    }

    @Override
    public E findInCacheOrLoadAndCreate(Context data) {
        return tryFindInCache(data).orElseGet(() -> createOrUpdateNow(load(data, newEntity())));
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
    public ImportDictionary getImportDictionary() {
        ImportDictionary dict = new ImportDictionary();
        getAutoImportMappings().stream()
                               .map(descriptor::getProperty)
                               .map(property -> property.tryAs(FieldDefinitionSupplier.class)
                                                        .map(FieldDefinitionSupplier::get)
                                                        .orElse(null))
                               .filter(Objects::nonNull)
                               .map(field -> expandAliases(field, aliases))
                               .forEach(dict::addField);

        return dict;
    }

    protected FieldDefinition expandAliases(FieldDefinition field, Extension aliases) {
        if (aliases != null && aliases.getConfig().hasPath(field.getName())) {
            aliases.getStringList(field.getName()).forEach(field::addAlias);
        }

        return field;
    }

    /**
     * Returns all mappings wearing a {@link AutoImport} annotation.
     *
     * @return a list of all mappings which are marked as auto import
     */
    protected List<Mapping> getAutoImportMappings() {
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> property.getAnnotation(AutoImport.class).isPresent())
                         .map(Property::getName)
                         .map(Mapping::named)
                         .collect(Collectors.toList());
    }

    @Override
    public ImportDictionary getExportDictionary() {
        ImportDictionary dict = new ImportDictionary();
        getExportableMappings().stream()
                               .map(descriptor::getProperty)
                               .map(property -> property.tryAs(FieldDefinitionSupplier.class)
                                                        .map(FieldDefinitionSupplier::get)
                                                        .orElse(null))
                               .filter(Objects::nonNull)
                               .map(field -> expandAliases(field, aliases))
                               .forEach(dict::addField);

        return dict;
    }

    /**
     * Returns all exportable mappings.
     * <p>
     * A mapping can be marked as exportable via four ways:
     * <ol>
     *    <li>By adding a {@link Exportable} annotation to the underlying field</li>
     *    <li>By providing an appropriate entry via {@link #collectDefaultExportableMappings(BiConsumer)}</li>
     *    <li>By providing an appropriate entry via {@link #collectExportableMappings(BiConsumer)}</li>
     *    <li>By providing an appropriate entry via a {@link EntityImportHandlerExtender}</li>
     * </ol>
     *
     * @return a list of all exportable mappings
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Explain("False positive - the check is done within the stream")
    protected List<Mapping> getExportableMappings() {
        List<ComparableTuple<Integer, Mapping>> priorizedList = new ArrayList<>();

        descriptor.getProperties()
                  .stream()
                  .filter(property -> property.getAnnotation(Exportable.class).isPresent())
                  .map(property -> ComparableTuple.create(property.getAnnotation(Exportable.class).get().priority(),
                                                          Mapping.named(property.getName())));
        collectDefaultExportableMappings((prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        collectExportableMappings((prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        for (EntityImportHandlerExtender extender : extenders) {
            extender.collectExportableMappings(this,
                                               descriptor,
                                               (prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        }
        Collections.sort(priorizedList);
        return Tuple.seconds(priorizedList);
    }

    /**
     * Collects all exportable mappings which are not provided via {@link Exportable} or
     * {@link #collectDefaultExportableMappings(BiConsumer)}.
     * <p>
     * These are columns/fields which can be exported but will not be contained in the default export.
     *
     * @param collector a collector to be supplied with additional columns to be exported
     */
    protected abstract void collectExportableMappings(BiConsumer<Integer, Mapping> collector);

    @Override
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Explain("False positive - the check is done within the stream")
    public List<String> getDefaultExportMapping() {
        List<ComparableTuple<Integer, Mapping>> priorizedList = new ArrayList<>();

        descriptor.getProperties()
                  .stream()
                  .filter(property -> property.getAnnotation(Exportable.class)
                                              .map(Exportable::autoExport)
                                              .orElse(false))
                  .map(property -> ComparableTuple.create(property.getAnnotation(Exportable.class).get().priority(),
                                                          Mapping.named(property.getName())));
        collectDefaultExportableMappings((prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        for (EntityImportHandlerExtender extender : extenders) {
            extender.collectDefaultExportableMappings(this,
                                                      descriptor,
                                                      (prio, name) -> priorizedList.add(ComparableTuple.create(prio,
                                                                                                               name)));
            extender.collectExportableMappings(this,
                                               descriptor,
                                               (prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        }
        Collections.sort(priorizedList);
        return priorizedList.stream().map(Tuple::getSecond).map(Mapping::toString).collect(Collectors.toList());
    }

    /**
     * Collects all exportable mappings which should be contained in the default export and that are not provided via
     * {@link Exportable}.
     *
     * @param collector a collector to be supplied with additional columns to be exported
     */
    protected abstract void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector);

    /**
     * Uses the appropriate property itself to extract a value from an entity.
     * <p>
     * Note that for {@link sirius.db.mixing.types.BaseEntityRef references} their <tt>ImportHandler</tt> is
     * determined and {@link #renderExportRepresentation(Object)} is invoked.
     *
     * @param fieldToExport the field or column to export
     * @return a function which extracts the field to be exported from a given entity
     */
    @Override
    public Function<E, Object> createExtractor(String fieldToExport) {
        for (EntityImportHandlerExtender extender : extenders) {
            Function<E, Object> result = extender.createExtractor(this, descriptor, context, fieldToExport);
            if (result != null) {
                return result;
            }
        }

        Property property = descriptor.findProperty(fieldToExport);
        if (property == null) {
            return null;
        }

        // Entitiy references are quite common, therefore we provide a default behaviour here,
        // but this shouldn't be done for every kind of property or use case as that's what
        // EntityImportHandlerExtender(s) are for
        if (property instanceof BaseEntityRefProperty) {
            ImportHandler<?> referencedImportHandler =
                    context.findHandler(((BaseEntityRefProperty<?, ?, ?>) property).getReferencedType());
            return entity -> {
                Object referencedId = property.getValue(entity);
                return referencedImportHandler.renderExportRepresentation(referencedId);
            };
        }

        // As stated above, string lists are quite common, therefore another default is provided here but great
        // care should be taken, so this method doesn't evolve into a god method / god class...
        if (property instanceof StringListProperty) {
            return entity -> Strings.join((List<?>) property.getValue(entity), ",");
        }

        return property::getValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object renderExportRepresentation(Object entityId) {
        if (entityId == null) {
            return null;
        }

        if (exportRepresentationMapping == null) {
            return entityId;
        }

        return fieldLookupCache.lookup((Class<? extends BaseEntity<?>>) descriptor.getType(),
                                       entityId,
                                       exportRepresentationMapping);
    }

    /**
     * Determines the export representation.
     * <p>
     * This will be used by {@link #renderExportRepresentation(Object)} to render a column value for a referenced ID of this type.
     * <p>
     * By default the column wearing an {@link Exportable} with {@link Exportable#defaultRepresentation()} set to <tt>true</tt>
     * and the lowest {@link Exportable#priority()} is used.
     *
     * @return the column / mapping to use when representing a entity of this type in an export
     */
    protected Mapping determineExportRepresentationMapping() {
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> property.getAnnotation(Exportable.class)
                                                     .map(Exportable::defaultRepresentation)
                                                     .orElse(false))
                         .min(Comparator.comparingInt(property -> property.getAnnotation(Exportable.class)
                                                                          .map(Exportable::priority)
                                                                          .orElse(999)))
                         .map(Property::getName)
                         .map(Mapping::named)
                         .orElse(null);
    }
}
