/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.StringListProperty;
import sirius.db.mixing.properties.StringProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for a {@link StringProperty}.
 */
@Register
public class StringListPropertyTransformer implements Transformer<StringListProperty, FieldDefinitionSupplier> {

    @Override
    public Class<StringListProperty> getSourceClass() {
        return StringListProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull StringListProperty property) {
        return () -> {
            FieldDefinition field =
                    new FieldDefinition(property.getName(), FieldDefinition.typeStringList());
            field.withLabel(property::getLabel);

            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            return field;
        };
    }
}
