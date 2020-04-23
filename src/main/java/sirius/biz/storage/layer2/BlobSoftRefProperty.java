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
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Handles fields of the type {@link BlobSoftRef} within an {@link sirius.db.mixing.BaseEntity}.
 */
public class BlobSoftRefProperty extends BlobRefProperty implements SQLPropertyInfo {

    /**
     * Contains the field length to use if URLs are also supported for this field.
     */
    protected static final int URL_COMPATIBLE_LENGTH = 512;

    /**
     * Factory for generating properties based on their field type
     */
    @Register(framework = Storage.FRAMEWORK_STORAGE)
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return BlobSoftRef.class.equals(field.getType());
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

            propertyConsumer.accept(new BlobSoftRefProperty(descriptor, accessPath, field));
        }
    }

    @Part
    private static Storage storage;

    private BlobSoftRefProperty(@Nonnull EntityDescriptor descriptor,
                                @Nonnull AccessPath accessPath,
                                @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    @Override
    protected void determineLengths() {
        BlobSoftRef templateReference = (BlobSoftRef) getRef(descriptor.getReferenceInstance());
        this.length =
                templateReference.isSupportsURL() ? URL_COMPATIBLE_LENGTH : BlobHardRefProperty.DEFAULT_KEY_LENGTH;
    }

    @Override
    protected void onBeforeSaveChecks(Object entity) {
        BlobHardRef ref = getRef(entity);
        if (isChanged(entity) && ref.isFilled() && ref.getBlob().isTemporary()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2: Cannot use a temporary object in a soft reference: %s for %s of %s",
                                    ref.getBlob().getBlobKey(),
                                    getField().getName(),
                                    entity)
                            .handle();
        }
    }
}
