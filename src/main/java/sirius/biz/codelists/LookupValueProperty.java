/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.db.mixing.properties.StringProperty;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Provides the propery used to handle {@link LookupValue lookup values} in database entities.
 */
public class LookupValueProperty extends StringProperty {

    private LookupValue referenceValue;

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return LookupValue.class.equals(field.getType());
        }

        @Override
        public void create(EntityDescriptor descriptor,
                           AccessPath accessPath,
                           Field field,
                           Consumer<Property> propertyConsumer) {
            if (!Modifier.isFinal(field.getModifiers())) {
                Mixing.LOG.WARN("Field %s in %s is not final! This will probably result in errors.",
                                field.getName(),
                                field.getDeclaringClass().getName());
            }

            propertyConsumer.accept(new LookupValueProperty(descriptor, accessPath, field));
        }
    }

    protected LookupValueProperty(@Nonnull EntityDescriptor descriptor,
                                  @Nonnull AccessPath accessPath,
                                  @Nonnull Field field) {
        super(descriptor, accessPath, field);
    }

    @Override
    protected void link() {
        referenceValue = getLookupValue(descriptor.getReferenceInstance());
    }

    public LookupValue getReferenceValue() {
        return referenceValue;
    }

    protected LookupValue getLookupValue(Object entity) {
        Object target = accessPath.apply(entity);
        return (LookupValue) super.getValueFromField(target);
    }

    @Override
    protected Object getValueFromField(Object target) {
        LookupValue lookupValue = (LookupValue) super.getValueFromField(target);
        return lookupValue.getValue();
    }

    @Override
    protected void setValueToField(Object value, Object target) {
        LookupValue lookupValue = (LookupValue) super.getValueFromField(target);
        lookupValue.setValue((String) value);
    }

    @Override
    public void onBeforeSaveChecks(Object entity) {
        String value = Value.of(getValue(entity)).trim();
        if (Strings.isFilled(value)) {
            if (((BaseEntity<?>) entity).isChanged(Mapping.named(getName()))) {
                Optional<String> normalizedValue = referenceValue.getTable().normalize(value);
                if (normalizedValue.isPresent()) {
                    setValue(entity, normalizedValue.get());
                } else if (referenceValue.getCustomValues() == LookupValue.CustomValues.REJECT) {
                    throw illegalFieldValue(Value.of(value));
                }
            }
        } else {
            setValue(entity, null);
        }

        super.onBeforeSaveChecks(entity);
    }

    @Override
    protected Object transformValueFromImport(Value value) {
        String importValue = value.trim();
        return referenceValue.getTable().normalizeInput(importValue).orElse(importValue);
    }
}
