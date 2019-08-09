/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.Property;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for all properties for which no customer transformer exists.
 */
@Register
public class PropertyFallbackTransformer implements Transformer<Property, FieldDefinitionSupplier> {

    @Override
    public int getPriority() {
        return 999;
    }

    @Override
    public Class<Property> getSourceClass() {
        return Property.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull Property property) {
        return () -> {
            FieldDefinition field = new FieldDefinition(property.getName(), FieldDefinition.typeOther());
            field.withLabel(property::getLabel);
            if (!property.isNullable()) {
                field.withCheck(new RequiredCheck());
            }

            return field;
        };
    }
}
