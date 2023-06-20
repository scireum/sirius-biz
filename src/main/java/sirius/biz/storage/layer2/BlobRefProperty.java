/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.db.es.ESPropertyInfo;
import sirius.db.es.IndexMappings;
import sirius.db.es.annotations.IndexMode;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.schema.SQLPropertyInfo;
import sirius.db.jdbc.schema.Table;
import sirius.db.jdbc.schema.TableColumn;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.sql.Types;

/**
 * Holder of common parts of blob reference property handling.
 * <p>
 * NOTE: This implementation assumes that BlobHardRef stays the parent class for all blob reference
 * property classes, which should be valid because hard and soft references should be enough.
 */
abstract class BlobRefProperty extends Property implements SQLPropertyInfo, ESPropertyInfo {

    protected static final int DEFAULT_KEY_LENGTH = 64;

    /**
     * Creates a new property for the given descriptor, access path and field.
     *
     * @param descriptor the descriptor which owns the property
     * @param accessPath the access path required to obtain the target object which contains the field
     * @param field      the field which stores the database value
     */
    protected BlobRefProperty(@Nonnull EntityDescriptor descriptor,
                              @Nonnull AccessPath accessPath,
                              @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    /**
     * Gets a {@link BlobHardRef} for the given entity.
     *
     * @param entity The entity to get the reference from
     * @return a {@link BlobHardRef} or a subclass of it
     */
    public BlobHardRef getRef(Object entity) {
        try {
            return (BlobHardRef) super.getValueFromField(entity);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(OMA.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Unable to obtain the BlobHardRef object from blob ref field ('%s' in '%s'): %s (%s)",
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
    protected Object transformToDatasource(Class<? extends BaseMapper<?, ?, ?>> mapperType, Object object) {
        return object;
    }

    @Override
    public Object transformFromDatasource(Class<? extends BaseMapper<?, ?, ?>> mapperType, Value object) {
        return object.get();
    }

    @Override
    public void setValue(Object entity, Object object) {
        Object target = accessPath.apply(entity);
        setValueToField(object, target);
    }

    /**
     * Sets the given value on the target entity as either a blob or a key depending on the value.
     *
     * @param value        The value to be set
     * @param targetEntity The entity to be set on
     */
    @Override
    protected void setValueToField(Object value, Object targetEntity) {
        BlobHardRef ref = getRef(targetEntity);

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
    public void describeProperty(ObjectNode description) {
        description.put(IndexMappings.MAPPING_TYPE, "keyword");
        transferOption(IndexMappings.MAPPING_STORED, getAnnotation(IndexMode.class), IndexMode::stored, description);
        transferOption(IndexMappings.MAPPING_INDEX, getAnnotation(IndexMode.class), IndexMode::indexed, description);
        transferOption(IndexMappings.MAPPING_DOC_VALUES,
                       getAnnotation(IndexMode.class),
                       IndexMode::docValues,
                       description);
    }

    /**
     * Checks if the blob reference on the entity has changed since loading from DB.
     *
     * @param entity the entity to check
     * @return <tt>true</tt> if changed, <tt>false</tt> otherwise
     */
    protected boolean isChanged(Object entity) {
        BaseEntity<?> baseEntity = (BaseEntity<?>) entity;
        return baseEntity.isChanged(nameAsMapping);
    }
}
