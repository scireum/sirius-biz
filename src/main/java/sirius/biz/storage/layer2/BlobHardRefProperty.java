/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.Storage;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.schema.SQLPropertyInfo;
import sirius.db.jdbc.schema.Table;
import sirius.db.jdbc.schema.TableColumn;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
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
 * Handles fields of the type {@link BlobHardRef} within an {@link BaseEntity}.
 */
public class BlobHardRefProperty extends Property implements SQLPropertyInfo {

    protected static final int DEFAULT_KEY_LENGTH = 64;

    /**
     * Factory for generating properties based on their field type
     */
    @Register(framework = Storage.FRAMEWORK_STORAGE)
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return BlobHardRef.class.equals(field.getType());
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

            propertyConsumer.accept(new BlobHardRefProperty(descriptor, accessPath, field));
        }
    }

    @Part
    private static BlobStorage storage;

    private BlobHardRefProperty(@Nonnull EntityDescriptor descriptor,
                                @Nonnull AccessPath accessPath,
                                @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    protected BlobHardRef getRef(Object entity) {
        try {
            return (BlobHardRef) super.getValueFromField(this.accessPath.apply(entity));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(OMA.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Unable to obtain a reference object from entity ref field ('%s' in '%s'): %s (%s)",
                                    getName(),
                                    descriptor.getType().getName())
                            .handle();
        }
    }

    @Override
    protected Object getValueFromField(Object target) {
        return getRef(target).getKey();
    }

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
    protected Object transformToMongo(Object object) {
        return object;
    }

    @Override
    protected Object transformFromMongo(Value object) {
        return object.get();
    }

    @Override
    public void setValue(Object entity, Object object) {
        this.setValueToField(object, entity);
    }

    @Override
    protected void setValueToField(Object value, Object target) {
        BlobHardRef ref = getRef(target);

        if (value == null || value instanceof Blob) {
            ref.setBlob((Blob) value);
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
        this.length = DEFAULT_KEY_LENGTH;
    }

    @Override
    protected void onBeforeSaveChecks(Object entity) {
        BlobHardRef ref = getRef(entity);
        if (ref.changed && ref.isFilled() && !ref.getBlob().isTemporary()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2: Cannot use a non temporary object in a hard reference: %s for %s of %s",
                                    ref.getBlob().getBlobKey(),
                                    getField().getName(),
                                    entity)
                            .handle();
        }
    }

    @Override
    protected void onAfterSave(Object entity) {
        BlobHardRef ref = getRef(entity);
        if (ref.changed) {
            BlobStorageSpace storageSpace = ref.getStorageSpace();
            String uniqueName = ((BaseEntity<?>) entity).getUniqueName();

            if (ref.isFilled()) {
                storageSpace.markAsUsed(uniqueName, getName(), ref.getKey());
            }
            storageSpace.deleteReferencedBlobs(uniqueName, getName(), ref.getKey());
        }
        ref.changed = false;
    }

    @Override
    protected void onAfterDelete(Object entity) {
        BlobHardRef ref = getRef(entity);
        if (ref.isFilled()) {
            ref.getBlob()
               .getStorageSpace()
               .deleteReferencedBlobs(((BaseEntity<?>) entity).getUniqueName(), getName(), null);
        }
    }
}
