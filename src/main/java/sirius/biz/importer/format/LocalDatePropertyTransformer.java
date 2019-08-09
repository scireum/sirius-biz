/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LocalDateProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for a {@link LocalDateProperty}.
 */
@Register
public class LocalDatePropertyTransformer implements Transformer<LocalDateProperty, FieldDefinitionSupplier> {

    @Override
    public Class<LocalDateProperty> getSourceClass() {
        return LocalDateProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull LocalDateProperty property) {
        return () -> {
            FieldDefinition field = new FieldDefinition(property.getName(), FieldDefinition.typeDate());
            field.withLabel(property::getLabel);
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            return field;
        };
    }
}
