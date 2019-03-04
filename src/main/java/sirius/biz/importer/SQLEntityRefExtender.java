package sirius.biz.importer;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.SQLEntityRefProperty;
import sirius.kernel.commons.Context;

import java.util.function.BiConsumer;

/**
 * Provides the basic logic to extend {@link SQLEntity entites} to find a referenced {@link sirius.db.jdbc.SQLEntityRef}.
 *
 * @param <E> the class of the entity referenced
 */
public abstract class SQLEntityRefExtender<E extends SQLEntity> implements EntityImportHandlerExtender {

    @Override
    public void collectLoaders(BaseImportHandler<BaseEntity<?>> handler,
                               EntityDescriptor descriptor,
                               ImporterContext context,
                               BiConsumer<Mapping, BiConsumer<Context, Object>> loaderCollector) {

        descriptor.getProperties()
                .stream()
                .filter(this::isReference)
                .forEach(property -> loaderCollector.accept(Mapping.named(property.getName()), (data, entity) -> {
                    if (canResolveProperty(property, data, entity) && entity.equals(property.getTarget(entity))) {
                        resolveProperty(context, property, data, entity);
                    } else {
                        // default logic if we can not resolve the property
                        property.setValue(entity, data.get(property.getName()));
                    }
                }));
    }

    private boolean isReference(Property property) {
        return property instanceof SQLEntityRefProperty
                && ((SQLEntityRefProperty) property).getReferencedType() == getType();
    }

    /**
     * Decides whether the default load logic should be applied or if the property should be resolved.
     *
     * @param property the property to check for the entity to import
     * @param data     the data to load into the entity
     * @param entity   the entity to import
     * @return <tt>true</tt> if then entity property should be resolved, <tt>false</tt> otherwise
     */
    protected abstract boolean canResolveProperty(Property property, Context data, Object entity);

    /**
     * Resolves a referenced entity from the data context to load.
     *
     * @param context  the current {@link ImporterContext}
     * @param property the property referencing the entity to resolve
     * @param data     the data to be used to resolve the referenced entity
     * @param entity   the entity referencing the entity to resolve
     */
    protected abstract void resolveProperty(ImporterContext context, Property property, Context data, Object entity);

    /**
     * Gets the class of the referenced entity.
     *
     * @return {@link Class} of the referenced entity
     */
    protected abstract Class<E> getType();
}
