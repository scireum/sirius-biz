/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.Property;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides the basic infrastructure to transform a {@link Property} into a {@link FieldDefinitionSupplier}.
 *
 * @param <S> the actual type of the property being transformed
 */
public abstract class BaseFieldDefinitionTransformer<S extends Property>
        implements Transformer<S, FieldDefinitionSupplier> {

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull S property) {
        return () -> {
            FieldDefinition field = new FieldDefinition(property.getName(), determineType(property));
            field.addAlias(property.getName());
            field.withLabel(property::getLabel);
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            customizeField(property, field);

            return field;
        };
    }

    /**
     * Determines the official type description to be used for this field.
     *
     * @param property the property to derive the type label from
     * @return a type description as generated e.g. by {@link FieldDefinition#typeString(Integer)} or the like
     */
    protected abstract String determineType(S property);

    /**
     * Further specializes the field definition based on the given property.
     *
     * @param property the property to derive infos from
     * @param field    the field to customize
     */
    protected void customizeField(S property, FieldDefinition field) {
        // Provides an empty default implementation...
    }
}
