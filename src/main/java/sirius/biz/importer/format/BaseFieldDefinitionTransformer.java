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

    protected abstract String determineType(S property);

    protected void customizeField(S property, FieldDefinition field) {
        // Provides an empty default implementation...
    }
}
