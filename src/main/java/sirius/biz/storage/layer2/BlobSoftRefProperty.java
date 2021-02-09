/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer2.jdbc.SQLBlob;
import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.biz.storage.layer2.mongo.MongoBlob;
import sirius.biz.storage.layer2.mongo.MongoBlobStorage;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.Sirius;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Handles fields of the type {@link BlobSoftRef} within an {@link sirius.db.mixing.BaseEntity}.
 */
public class BlobSoftRefProperty extends BlobRefProperty {

    @Part
    protected static Mixing mixing;

    /**
     * Contains the field length to use if URLs are also supported for this field.
     */
    protected static final int URL_COMPATIBLE_LENGTH = 512;

    private static final String PARAM_TYPE = "type";
    private static final String PARAM_OWNER = "owner";
    private static final String PARAM_FIELD = "field";

    private BlobSoftRef blobSoftRef;

    /**
     * Factory for generating properties based on their field type
     */
    @Register(framework = StorageUtils.FRAMEWORK_STORAGE)
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

    private BlobSoftRefProperty(@Nonnull EntityDescriptor descriptor,
                                @Nonnull AccessPath accessPath,
                                @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    private void forEachBlobType(Consumer<EntityDescriptor> callback) {
        if (Sirius.isFrameworkEnabled(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)) {
            callback.accept(mixing.getDescriptor(SQLBlob.class));
        }
        if (Sirius.isFrameworkEnabled(MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)) {
            callback.accept(mixing.getDescriptor(MongoBlob.class));
        }
    }

    private BlobSoftRef getBlobSoftRef() {
        if (blobSoftRef == null) {
            try {
                blobSoftRef = (BlobSoftRef) field.get(accessPath.apply(descriptor.getReferenceInstance()));
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(Mixing.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Unable to obtain a BlobSoftRef object from entity ref field ('%s' in '%s'): %s (%s)",
                                        getName(),
                                        descriptor.getType().getName())
                                .handle();
            }
        }

        return blobSoftRef;
    }

    @Override
    protected void link() {
        super.link();

        try {
            BaseEntityRef.OnDelete deleteHandler = getBlobSoftRef().getDeleteHandler();

            if (deleteHandler == BaseEntityRef.OnDelete.CASCADE) {
                forEachBlobType(entityDescriptor -> entityDescriptor.addCascadeDeleteHandler(this::onDeleteCascade));
            } else if (deleteHandler == BaseEntityRef.OnDelete.SET_NULL) {
                if (!isNullable()) {
                    Mixing.LOG.WARN("Error in property %s of %s: The field is not marked as NullAllowed,"
                                    + " therefore SET_NULL is not a valid delete handler!", this, getDescriptor());
                }

                forEachBlobType(entityDescriptor -> entityDescriptor.addCascadeDeleteHandler(this::onDeleteSetNull));
            }
        } catch (Exception e) {
            Mixing.LOG.WARN("Error when linking property %s of %s: %s (%s)",
                            this,
                            getDescriptor(),
                            e.getMessage(),
                            e.getClass().getSimpleName());
        }
    }

    protected void onDeleteCascade(Object entity) {
        if (!Strings.areEqual(blobSoftRef.getSpace(), ((Blob) entity).getSpaceName())) {
            return;
        }

        TaskContext taskContext = TaskContext.get();

        taskContext.smartLogLimited(() -> NLS.fmtr("BaseEntityRefProperty.cascadeDelete")
                                             .set(PARAM_TYPE, getDescriptor().getPluralLabel())
                                             .set(PARAM_OWNER, Strings.limit(entity, 30))
                                             .set(PARAM_FIELD, getLabel())
                                             .format());

        processReferenceInstances(entity, reference -> cascadeDelete(taskContext, reference));
    }

    private void cascadeDelete(TaskContext taskContext, BaseEntity<?> other) {
        Watch watch = Watch.start();
        other.getMapper().delete(other);
        taskContext.addTiming(NLS.get("BaseEntityRefProperty.cascadedDelete"), watch.elapsedMillis(), true);
    }

    protected void onDeleteSetNull(Object entity) {
        if (!Strings.areEqual(blobSoftRef.getSpace(), ((Blob) entity).getSpaceName())) {
            return;
        }

        TaskContext taskContext = TaskContext.get();
        taskContext.smartLogLimited(() -> NLS.fmtr("BaseEntityRefProperty.cascadeSetNull")
                                             .set(PARAM_TYPE, getDescriptor().getPluralLabel())
                                             .set(PARAM_OWNER, Strings.limit(entity, 30))
                                             .set(PARAM_FIELD, getLabel())
                                             .format());

        processReferenceInstances(entity, reference -> cascadeSetNull(taskContext, reference));
    }

    private void processReferenceInstances(Object entity, Consumer<BaseEntity<?>> handler) {
        BaseEntity<?> referenceInstance = (BaseEntity<?>) getDescriptor().getReferenceInstance();
        referenceInstance.getMapper()
                         .select(referenceInstance.getClass())
                         .eq(nameAsMapping, ((Blob) entity).getBlobKey())
                         .iterateAll(handler);
    }

    private void cascadeSetNull(TaskContext taskContext, BaseEntity<?> other) {
        Watch watch = Watch.start();
        setValue(other, "");
        other.getMapper().update(other);
        taskContext.addTiming(NLS.get("BaseEntityRefProperty.cascadedSetNull"), watch.elapsedMillis());
    }

    @Override
    protected void determineLengths() {
        BlobSoftRef templateReference = (BlobSoftRef) getRef(descriptor.getReferenceInstance());
        this.length =
                templateReference.isSupportsURL() ? URL_COMPATIBLE_LENGTH : BlobHardRefProperty.DEFAULT_KEY_LENGTH;
    }

    @Override
    protected void onBeforeSaveChecks(Object entity) {
        BlobSoftRef ref = (BlobSoftRef) getRef(entity);
        if (isChanged(entity) && ref.isFilled() && !ref.isURL() && ref.getBlob().isTemporary()) {
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
