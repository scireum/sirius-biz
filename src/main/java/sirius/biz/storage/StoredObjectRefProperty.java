/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import sirius.db.mixing.AccessPath;
import sirius.db.mixing.Entity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.OMA;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
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
 * Handles fields of the type {@link StoredObjectRef} within an {@link Entity}.
 */
public class StoredObjectRefProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
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
            return (StoredObjectRef) super.getValueFromField(entity);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(OMA.LOG)
                            .error(e)
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
        throw new UnsupportedOperationException(
                "As we cannot safely determine the tenant, we cannot lookup a matching StoredObject");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setValueToField(Object value, Object target) {
        StoredObjectRef ref = getStoredObjectRef(target);
        if (value == null || value instanceof StoredObject) {
            ref.setObject((StoredObject) value);
        } else {
            ref.setKey((String) value);
        }
    }

    @Override
    protected int getSQLType() {
        return Types.CHAR;
    }

    @Override
    protected void determineLengths() {
        StoredObjectRef templateReference = getStoredObjectRef(descriptor.getReferenceInstance());
        this.length = templateReference.isSupportsURL() ? 512 : 64;
    }

    @Override
    protected void onAfterSave(Entity entity) {
        StoredObjectRef ref = getStoredObjectRef(entity);
        if (ref.changed) {
            String reference = field.getName() + ":" + entity.getUniqueName();
            storage.deleteReferencedObjects(reference, ref.getKey());
            storage.markAsUsed(ref.getKey());
            ref.changed = false;
        }
    }

    @Override
    protected void onAfterDelete(Entity entity) {
        storage.deleteReferencedObjects(field.getName() + ":" + entity.getUniqueName(), null);
    }
}
