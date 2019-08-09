/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.annotations.Numeric;
import sirius.db.mixing.properties.AmountProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Generates a {@link FieldDefinition} for an {@link AmountProperty}.
 */
@Register
public class AmountPropertyTransformer implements Transformer<AmountProperty, FieldDefinitionSupplier> {

    @Override
    public Class<AmountProperty> getSourceClass() {
        return AmountProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull AmountProperty property) {
        return () -> {
            Optional<Numeric> numeric = property.getAnnotation(Numeric.class);
            String type = FieldDefinition.typeNumber(numeric.map(Numeric::precision).orElse(0),
                                                     numeric.map(Numeric::scale).orElse(0));
            FieldDefinition field = new FieldDefinition(property.getName(), type);
            field.withLabel(property::getLabel);
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            return field;
        };
    }
}
