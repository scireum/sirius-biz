/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LocalDateTimeProperty;
import sirius.db.mixing.properties.LocalTimeProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for a {@link LocalDateTimeProperty}.
 */
@Register
public class LocalTimePropertyTransformer implements Transformer<LocalTimeProperty, FieldDefinitionSupplier> {

    @Override
    public Class<LocalTimeProperty> getSourceClass() {
        return LocalTimeProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull LocalTimeProperty property) {
        return () -> {
            FieldDefinition field = new FieldDefinition(property.getName(), FieldDefinition.typeTime());
            field.withLabel(property::getLabel);
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            return field;
        };
    }
}
