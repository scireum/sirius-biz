/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LongProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for a {@link LongProperty}.
 */
@Register
public class LongPropertyTransformer implements Transformer<LongProperty, FieldDefinitionSupplier> {

    @Override
    public Class<LongProperty> getSourceClass() {
        return LongProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull LongProperty property) {
        return () -> {
            FieldDefinition field = new FieldDefinition(property.getName(), FieldDefinition.typeNumber(0, 0));
            field.withLabel(property::getLabel);
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }


            return field;
        };
    }
}
