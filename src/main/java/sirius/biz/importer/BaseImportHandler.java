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
import sirius.biz.importer.txn.ImportTransactionHelper;
import sirius.biz.importer.txn.ImportTransactionalEntity;
import sirius.biz.process.ErrorContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.protocol.Journaled;
import sirius.biz.protocol.TraceData;
import sirius.biz.protocol.Traced;
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
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
    private final Extension aliases;

    /**
     * Defines a context key used to skip loading entities aborted via {@linkplain sirius.biz.scripting.ScriptableEvent script}
     */
    public static final String SCRIPT_ABORTED = "_SCRIPT_ABORTED_";

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
        if (context.getEventHandler().isActive()) {
            BeforeLoadEvent<E> beforeLoadEvent = new BeforeLoadEvent<>(entity, data, context);
            context.getEventHandler().handleEvent(beforeLoadEvent);
            if (beforeLoadEvent.isAborted()) {
                data.put(SCRIPT_ABORTED, true);
                return null;
            }
        }

        Arrays.stream(mappings).forEach(mapping -> loadMapping(entity, mapping, data));
        enforcePostLoadConstraints(entity);

        return entity;
    }

    /**
     * Enforces some consistency checks after all values have been loaded.
     *
     * @param entity the entity to check
     */
    protected void enforcePostLoadConstraints(E entity) {
        if (entity instanceof TenantAware tenantAwareEntity) {
            tenantAwareEntity.setOrVerifyCurrentTenant();
        }
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
        if (property instanceof BaseEntityRefProperty) {
            Class<?> referencedType = ((BaseEntityRefProperty<?, ?, ?>) property).getReferencedType();
            if (value.is(referencedType)) {
                property.parseValueFromImport(entity, value);
                return;
            }
        }

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

    @Override
    public void enforcePreSaveConstraints(E entity) {
        if (entity instanceof TenantAware tenantAwareEntity) {
            tenantAwareEntity.setOrVerifyCurrentTenant();
        }

        if (entity instanceof Journaled journaledEntity) {
            journaledEntity.getJournal().enableBatchLog();
        }

        if (entity instanceof ImportTransactionalEntity transactionalEntity) {
            markTransaction(transactionalEntity);
        }
    }

    /**
     * Marks the given entity with the current opened transaction id.
     *
     * @param transactionalEntity the entity to mark
     * @see ImportTransactionHelper
     */
    protected void markTransaction(ImportTransactionalEntity transactionalEntity) {
        ImportTransactionHelper importTransactionHelper =
                context.getImporter().findHelper(ImportTransactionHelper.class);

        if (importTransactionHelper.isActive()) {
            importTransactionHelper.mark(transactionalEntity);
        }
    }

    /**
     * Enforces some consistency checks before a deletion is performed.
     * <p>
     * This method should only rarely be overwritten as most checks should be either be performed during a load
     * or within the <tt>beforeDelete</tt> checks of the entity. The main point of this method is enforcing the
     * correct tenant before deleting, as some import handlers might yield (readonly) entities from parent
     * tenants.
     *
     * @param entity the entity to check
     */
    protected void enforcePreDeleteConstraints(E entity) {
        // By default, the constraints are the same as when the entity is saved...
        enforcePreSaveConstraints(entity);
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
    public HandledException fail(@Nullable String referenceNumber,
                                 @Nullable Context data,
                                 Object... relevantContextFields) {
        return enhanceExceptionWithHints(Exceptions.createHandled()
                                                   .withDirectMessage(createErrorMessage(referenceNumber,
                                                                                         data,
                                                                                         relevantContextFields))
                                                   .handle());
    }

    protected String createErrorMessage(String referenceNumber, Context data, Object... relevantContextFields) {
        StringBuilder message = new StringBuilder();
        if (Strings.isFilled(referenceNumber)) {
            message.append("'");
            message.append(referenceNumber);
            message.append("' ");
        }
        if (data != null && relevantContextFields.length > 0) {
            boolean hasReference = !message.isEmpty();
            if (hasReference) {
                message.append("(");
            }
            Monoflop firstContextField = Monoflop.create();
            for (Object field : relevantContextFields) {
                if (firstContextField.successiveCall()) {
                    message.append(", ");
                }
                message.append(getImportDictionary().expandToLabel(field.toString()));
                message.append(": ");
                message.append("'");
                message.append(NLS.toUserString(data.getValue(field.toString()).replaceEmptyWith("-")));
                message.append("'");
            }
            if (hasReference) {
                message.append(") ");
            } else {
                message.append(" ");
            }
        }

        message.append(createCannotResolveMessage());

        return message.toString();
    }

    protected String createCannotResolveMessage() {
        return NLS.fmtr("BaseImportHandler.cannotResolveMessage").set("type", descriptor.getLabel()).format();
    }

    @Override
    public E warn(@Nullable String referenceNumber, @Nullable Context data, Object... relevantContextFields) {
        return warn(null, referenceNumber, data, relevantContextFields);
    }

    @Override
    public E warn(@Nullable E defaultValue,
                  @Nullable String referenceNumber,
                  @Nullable Context data,
                  Object... relevantContextFields) {
        ErrorContext.get().logExceptionAsWarning(fail(referenceNumber, data, relevantContextFields));
        return defaultValue;
    }

    @Override
    public E findOrFail(Context data) {
        return tryFind(data).orElseThrow(() -> fail(null, data));
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

    @Override
    @SuppressWarnings("unchecked")
    public E newEntity() {
        try {
            return (E) descriptor.getType().getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException exception) {
            throw Exceptions.handle()
                            .error(exception)
                            .withSystemErrorMessage("Cannot create an instance of: %s", descriptor.getType().getName())
                            .handle();
        }
    }

    @Override
    public ImportDictionary getImportDictionary() {
        ImportDictionary importDictionary = new ImportDictionary();
        importDictionary.withCustomFieldLookup(this::findComputedField);

        List<FieldDefinition> fields = new ArrayList<>();
        for (Mapping mapping : getAutoImportMappings()) {
            Property property = descriptor.findProperty(mapping.toString());
            if (property != null) {
                property.tryAs(FieldDefinitionSupplier.class)
                        .map(FieldDefinitionSupplier::get)
                        .map(this::expandAliases)
                        .ifPresent(fields::add);
            } else {
                importDictionary.findField(mapping.toString()).map(this::expandAliases).ifPresent(fields::add);
            }
        }

        fields.sort(Comparator.comparing(FieldDefinition::getLabel));
        fields.forEach(importDictionary::addField);

        return importDictionary;
    }

    /**
     * Returns all mappings wearing a {@link AutoImport} annotation.
     *
     * @return a list of all mappings which are marked as auto import
     */
    protected List<Mapping> getAutoImportMappings() {
        UserInfo currentUser = UserContext.getCurrentUser();
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> property.getAnnotation(AutoImport.class)
                                                     .map(AutoImport::permissions)
                                                     .map(currentUser::hasPermissions)
                                                     .orElse(false))
                         .map(Property::getName)
                         .map(Mapping::named)
                         .filter(this::isAutoImportMappingAccepted)
                         .toList();
    }

    /**
     * Returns all mappings which should be checked for changes to determine if the persisted entity should be
     * updated.
     *
     * @return a list of all mappings which should be checked for changes
     */
    protected List<Mapping> getMappingsToCheckForChanges() {
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> !property.isAnnotationPresent(IgnoreInImportChangedCheck.class))
                         .map(Property::getName)
                         .map(Mapping::named)
                         .toList();
    }

    /**
     * Determines if an auto imported mapping should be put into the <tt>ImportDictionary</tt>.
     * <p>
     * This method can be overwritten to suppress properties which wear an <tt>AutoImport</tt> annotation
     * from being put into the <tt>ImportDictionary</tt>.
     * <p>
     * By default, this will constantly return <tt>true</tt>.
     *
     * @param mapping the mapping to evaluate
     * @return <tt>true</tt> to accept the mapping, <tt>false</tt> otherwise
     */
    protected boolean isAutoImportMappingAccepted(Mapping mapping) {
        return true;
    }

    /**
     * Uses the aliases provided in the system configuration and applies them to the given field.
     *
     * @param field the field to expand the aliases for
     * @return the field with expanded aliases
     */
    protected FieldDefinition expandAliases(FieldDefinition field) {
        if (aliases != null && aliases.getConfig().hasPath(field.getName())) {
            aliases.getStringList(field.getName()).forEach(alias -> {
                if (Sirius.isDev() && field.getAliases().contains(alias)) {
                    Importer.LOG.WARN("%s (for %s) has a duplicate alias: %s (Check configuration?)",
                                      getClass().getName(),
                                      newEntity().getClass().getName(),
                                      alias);
                }

                field.addTranslatedAliases(alias);
            });
        }

        return field;
    }

    /**
     * Obtains the maximum amount of {@link sirius.biz.process.logs.ProcessLog entries} should be logged
     * for the specific entity.
     * <p>
     * Override this method in order to provide a new limit or return 0 to skip limiting completely.
     *
     * @return the amount of messages to limit
     */
    protected int obtainMessageTypeLimit() {
        return ProcessLog.MESSAGE_TYPE_COUNT_MEDIUM;
    }

    /**
     * Returns the entity label key used to categorize messages for errors.
     * <p>
     * This will be put into {@link ProcessLog#HINT_MESSAGE_TYPE}.
     *
     * @return the message type to use
     */
    protected String obtainMessageType() {
        return "$" + descriptor.getLabelKey();
    }

    /**
     * Includes process hints to the {@link HandledException} thrown when saving an entity.
     * <p>
     * This permits processes to categorize these errors under the entity's label.
     * The key won't be added if already present, so in rare circumstances the caller might want to provide
     * a different content.
     *
     * @param exception a {@link HandledException} thrown saving an entity
     * @return the enriched exception
     */
    protected HandledException enhanceExceptionWithHints(HandledException exception) {
        if (exception.getHint(ProcessLog.HINT_MESSAGE_TYPE).isEmptyString()) {
            exception.withHint(ProcessLog.HINT_MESSAGE_TYPE, obtainMessageType());
        }
        if (obtainMessageTypeLimit() > 0) {
            exception.withHint(ProcessLog.HINT_MESSAGE_COUNT, obtainMessageTypeLimit());
        }
        return exception;
    }

    @Override
    public ImportDictionary getExportDictionary() {
        ImportDictionary exportDictionary = new ImportDictionary();
        exportDictionary.withCustomFieldLookup(this::findComputedField);

        for (Mapping mapping : getExportableMappings()) {
            Property property = descriptor.findProperty(mapping.toString());
            if (property != null) {
                property.tryAs(FieldDefinitionSupplier.class)
                        .map(FieldDefinitionSupplier::get)
                        .map(this::expandAliases)
                        .ifPresent(exportDictionary::addField);
            } else {
                exportDictionary.findField(mapping.toString())
                                .map(this::expandAliases)
                                .ifPresent(exportDictionary::addField);
            }
        }

        return exportDictionary;
    }

    /**
     * Resolves a field into a <tt>FieldDefinition</tt>.
     * <p>
     * For fields which are unknown as property of our <tt>descriptor</tt>, these come most probably from one of our
     * <tt>EntityImportHandlerExtenders</tt>, we therefore iterate over them and hope for the best.
     * <p>
     * This method must be overwritten, if the import handler itself emits virtual fields, which are unknown to the
     * descriptor or an import extender.
     *
     * @param field the field to resolve
     * @return a field definition for the field or <tt>null</tt> if the field is unknown
     */
    @Nullable
    protected FieldDefinition findComputedField(String field) {
        for (EntityImportHandlerExtender entityImportHandlerExtender : extenders) {
            FieldDefinition result = entityImportHandlerExtender.resolveCustomField(this, descriptor, field);
            if (result != null) {
                return result;
            }
        }
        return null;
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
    protected List<Mapping> getExportableMappings() {
        List<ComparableTuple<Integer, Mapping>> priorizedList = new ArrayList<>();
        UserInfo currentUser = UserContext.getCurrentUser();

        descriptor.getProperties()
                  .stream()
                  .filter(property -> isExportable(currentUser, property))
                  .map(property -> ComparableTuple.create(property.getAnnotation(Exportable.class)
                                                                  .map(Exportable::priority)
                                                                  .orElse(0), Mapping.named(property.getName())))
                  .forEach(priorizedList::add);

        collectDefaultExportableMappings((prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        collectExportableMappings((prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));

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
        UserInfo currentUser = UserContext.getCurrentUser();
        List<ComparableTuple<Integer, Mapping>> priorizedList = new ArrayList<>();

        descriptor.getProperties()
                  .stream()
                  .filter(property -> isExportable(currentUser, property))
                  .filter(this::isAutoExport)
                  .map(property -> ComparableTuple.create(property.getAnnotation(Exportable.class).get().priority(),
                                                          Mapping.named(property.getName())))
                  .forEach(priorizedList::add);
        collectDefaultExportableMappings((prio, name) -> priorizedList.add(ComparableTuple.create(prio, name)));
        for (EntityImportHandlerExtender extender : extenders) {
            extender.collectDefaultExportableMappings(this,
                                                      descriptor,
                                                      (prio, name) -> priorizedList.add(ComparableTuple.create(prio,
                                                                                                               name)));
        }
        Collections.sort(priorizedList);
        return priorizedList.stream().map(Tuple::getSecond).map(Mapping::toString).toList();
    }

    protected boolean isExportable(UserInfo currentUser, Property property) {
        return property.getAnnotation(Exportable.class)
                       .map(Exportable::permissions)
                       .map(currentUser::hasPermissions)
                       .orElse(false);
    }

    protected boolean isAutoExport(Property property) {
        return property.getAnnotation(Exportable.class).map(Exportable::autoExport).orElse(false);
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
    public Function<? super E, ?> createExtractor(String fieldToExport) {
        for (EntityImportHandlerExtender extender : extenders) {
            Function<? super E, ?> result = extender.createExtractor(this, descriptor, context, fieldToExport);
            if (result != null) {
                return result;
            }
        }

        Property property = descriptor.findProperty(fieldToExport);
        if (property == null) {
            return null;
        }

        // Entity references are quite common, therefore we provide a default behaviour here,
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
     * @return the column / mapping to use when representing an entity of this type in an export
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
