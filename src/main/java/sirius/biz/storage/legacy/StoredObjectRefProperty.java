/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.schema.SQLPropertyInfo;
import sirius.db.jdbc.schema.Table;
import sirius.db.jdbc.schema.TableColumn;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Types;
import java.util.function.Consumer;

/**
 * Handles fields of the type {@link StoredObjectRef} within an {@link SQLEntity}.
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
public class StoredObjectRefProperty extends Property implements SQLPropertyInfo {

    /**
     * Factory for generating properties based on their field type
     */
    @Register(framework = Storage.FRAMEWORK_STORAGE)
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return StoredObjectRef.class.equals(field.getType());
        }

        @Override
        public void create(EntityDescriptor descriptor,
                           AccessPath accessPath,
                           Field field,
                           Consumer<Property> propertyConsumer) {
            if (!Modifier.isFinal(field.getModifiers())) {
                OMA.LOG.WARN("Field %s in %s is not final! This will probably result in errors.",
                             field.getName(),
                             field.getDeclaringClass().getName());
            }

            propertyConsumer.accept(new StoredObjectRefProperty(descriptor, accessPath, field));
        }
    }

    @Part
    private static Storage storage;

    private StoredObjectRefProperty(@Nonnull EntityDescriptor descriptor,
                                    @Nonnull AccessPath accessPath,
                                    @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    protected StoredObjectRef getStoredObjectRef(Object entity) {
        try {
            return (StoredObjectRef) super.getValueFromField(this.accessPath.apply(entity));
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(OMA.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Unable to obtain StoredObjectRef object from entity ref field ('%s' in '%s'): %s (%s)",
                                    getName(),
                                    descriptor.getType().getName())
                            .handle();
        }
    }

    @Override
    protected Object getValueFromField(Object target) {
        return getStoredObjectRef(target).getKey();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object transformValue(Value value) {
        return value.get();
    }

    @Override
    protected Object transformToJDBC(Object object) {
        return object;
    }

    @Override
    protected Object transformFromJDBC(Value object) {
        return object.get();
    }

    @Override
    public void setValue(Object entity, Object object) {
        this.setValueToField(object, entity);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setValueToField(Object value, Object target) {
        StoredObjectRef ref = getStoredObjectRef(target);
        initializeReference((SQLEntity) target, ref);
        if (value == null || value instanceof StoredObject) {
            ref.setObject((StoredObject) value);
        } else {
            ref.setKey((String) value);
        }
    }

    @Override
    public void contributeToTable(Table table) {
        table.getColumns().add(new TableColumn(this, Types.CHAR));
    }

    @Override
    protected void determineLengths() {
        StoredObjectRef templateReference = getStoredObjectRef(descriptor.getReferenceInstance());
        this.length = templateReference.isSupportsURL() ? 512 : 64;
    }

    @Override
    protected void onAfterSave(Object entity) {
        StoredObjectRef ref = getStoredObjectRef(entity);
        initializeReference((BaseEntity<?>) entity, ref);
        if (ref.changed && Strings.isFilled(ref.reference)) {
            storage.deleteReferencedObjects(ref.reference, ref.getKey());
            storage.markAsUsed(ref.reference, ref.getKey());
            ref.changed = false;
        }
    }

    private void initializeReference(BaseEntity<?> entity, StoredObjectRef ref) {
        if (entity.isNew()) {
            ref.reference = null;
        } else {
            ref.reference = field.getName() + ":" + entity.getUniqueName();
        }
    }

    @Override
    protected void onAfterDelete(Object entity) {
        StoredObjectRef ref = getStoredObjectRef(entity);
        if (Strings.isFilled(ref.reference)) {
            storage.deleteReferencedObjects(ref.reference, null);
        }
    }
}
