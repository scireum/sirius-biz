/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.StringProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for a {@link StringProperty}.
 */
@Register
public class StringPropertyTransformer implements Transformer<StringProperty, FieldDefinitionSupplier> {

    @Override
    public Class<StringProperty> getSourceClass() {
        return StringProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull StringProperty property) {
        return () -> {
            FieldDefinition field =
                    new FieldDefinition(property.getName(), FieldDefinition.typeString(property.getLength()));
            field.withLabel(property::getLabel);
            if (property.getLength() > 0) {
                field.withCheck(new LengthCheck(property.getLength()));
            }
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            return field;
        };
    }
}
