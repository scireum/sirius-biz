/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Handles fields of the type {@link BlobHardRef} within an {@link BaseEntity}.
 */
public class BlobHardRefProperty extends BlobRefProperty {

    /**
     * Factory for generating properties based on their field type
     */
    @Register(framework = StorageUtils.FRAMEWORK_STORAGE)
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
    @Nullable
    private static BlobStorage storage;

    private BlobHardRefProperty(@Nonnull EntityDescriptor descriptor,
                                @Nonnull AccessPath accessPath,
                                @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    @Override
    protected void determineLengths() {
        this.length = DEFAULT_KEY_LENGTH;
    }

    @Override
    protected void onBeforeSaveChecks(Object entity) {
        BlobHardRef ref = getRef(this.accessPath.apply(entity));
        if (isChanged(entity) && ref.isFilled() && !ref.getBlob().isTemporary()) {
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
        BlobHardRef ref = getRef(this.accessPath.apply(entity));
        if (isChanged(entity)) {
            BlobStorageSpace storageSpace = ref.getStorageSpace();
            String uniqueName = ((BaseEntity<?>) entity).getUniqueName();

            if (ref.isFilled()) {
                storageSpace.markAsUsed(uniqueName, getName(), ref.getKey());
            }
            storageSpace.deleteReferencedBlobs(uniqueName, getName(), ref.getKey());
        }
    }

    @Override
    protected void onAfterDelete(Object entity) {
        BlobHardRef ref = getRef(this.accessPath.apply(entity));
        if (ref.isFilled()) {
            ref.getBlob()
               .getStorageSpace()
               .deleteReferencedBlobs(((BaseEntity<?>) entity).getUniqueName(), getName(), null);
        }
    }
}
