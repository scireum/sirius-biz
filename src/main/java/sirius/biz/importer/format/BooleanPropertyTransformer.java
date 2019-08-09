/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.BooleanProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates a {@link FieldDefinition} for a {@link BooleanProperty}.
 */
@Register
public class BooleanPropertyTransformer implements Transformer<BooleanProperty, FieldDefinitionSupplier> {

    @Override
    public Class<BooleanProperty> getSourceClass() {
        return BooleanProperty.class;
    }

    @Override
    public Class<FieldDefinitionSupplier> getTargetClass() {
        return FieldDefinitionSupplier.class;
    }

    @Nullable
    @Override
    public FieldDefinitionSupplier make(@Nonnull BooleanProperty property) {
        return () -> {
            FieldDefinition field = new FieldDefinition(property.getName(), FieldDefinition.typeBoolean());
            field.withLabel(property::getLabel);
            field.withCheck(new ValueInListCheck("true",
                                                 "false",
                                                 "$" + NLS.CommonKeys.YES.key(),
                                                 "$" + NLS.CommonKeys.NO.key()));

            return field;
        };
    }
}
