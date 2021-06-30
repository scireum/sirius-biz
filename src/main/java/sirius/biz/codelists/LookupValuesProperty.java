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
import sirius.db.mixing.properties.StringListProperty;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Provides the propery used to handle {@link LookupValues lookup values} in database entities.
 */
public class LookupValuesProperty extends StringListProperty {

    private LookupValues referenceValues;

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return LookupValues.class.equals(field.getType());
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

            propertyConsumer.accept(new LookupValuesProperty(descriptor, accessPath, field));
        }
    }

    protected LookupValuesProperty(EntityDescriptor descriptor, AccessPath accessPath, Field field) {
        super(descriptor, accessPath, field);
    }

    @Override
    protected void link() {
        referenceValues = getLookupValues(descriptor.getReferenceInstance());
    }

    public LookupValues getReferenceValues() {
        return referenceValues;
    }

    /**
     * Bypasses {@link StringListProperty#getValueFromField(java.lang.Object)} to access the actual LookupValues entity.
     *
     * @param entity the target object determined by the access path
     * @return the corresponding {@link LookupValues} object determined by the access path
     * @see Property#getValueFromField(java.lang.Object)
     */
    protected LookupValues getLookupValues(Object entity) {
        Object target = accessPath.apply(entity);
        try {
            return (LookupValues) field.get(target);
        } catch (IllegalAccessException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Cannot read property '%s' (from '%s'): %s (%s)",
                                                    getName(),
                                                    getDefinition())
                            .handle();
        }
    }

    @Override
    public void onBeforeSaveChecks(Object entity) {
        if (((BaseEntity<?>) entity).isChanged(Mapping.named(getName()))) {
            LookupValues lookupValues = getLookupValues(entity);
            List<String> normalizedValues = lookupValues.data().stream().map(value -> {
                Optional<String> normalizedValue = referenceValues.getTable().normalize(value);
                if (normalizedValue.isPresent()) {
                    return normalizedValue.get();
                } else if (referenceValues.getCustomValues() == LookupValue.CustomValues.REJECT) {
                    throw illegalFieldValue(Value.of(value));
                } else {
                    return value;
                }
            }).collect(Collectors.toList());

            lookupValues.setData(normalizedValues);
        }

        super.onBeforeSaveChecks(entity);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object transformValueFromImport(Value value) {
        LookupTable table = referenceValues.getTable();
        List<String> values = (List<String>) super.transformValueFromImport(value);
        return values.stream()
                     .map(importValue -> table.normalizeInput(importValue).orElse(importValue))
                     .collect(Collectors.toList());
    }
}
