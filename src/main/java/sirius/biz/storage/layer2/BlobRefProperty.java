/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.db.jdbc.OMA;
import sirius.db.jdbc.schema.SQLPropertyInfo;
import sirius.db.jdbc.schema.Table;
import sirius.db.jdbc.schema.TableColumn;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.sql.Types;

public abstract class BlobRefProperty extends Property implements SQLPropertyInfo {

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
    public void setValue(Object entity, Object object) {
        this.setValueToField(object, entity);
    }

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

    protected boolean isChanged(Object entity) {
        BaseEntity baseEntity = (BaseEntity) entity;
        return baseEntity.isChanged(nameAsMapping);
    }
}
