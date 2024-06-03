/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.EnumProperty;
import sirius.kernel.di.std.Register;

import java.util.Arrays;

/**
 * Generates a {@link FieldDefinition} for an {@link EnumProperty}.
 */
@Register
public class EnumPropertyTransformer extends BaseFieldDefinitionTransformer<EnumProperty> {

    @Override
    public Class<EnumProperty> getSourceClass() {
        return EnumProperty.class;
    }

    @Override
    protected String determineType(EnumProperty property) {
        return FieldDefinition.typeOther();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void customizeField(EnumProperty property, FieldDefinition field) {
        field.withCheck(new ValueInListCheck(Arrays.stream(((Class<Enum<?>>) property.getField()
                                                                                     .getType()).getEnumConstants())
                                                   .map(Enum::name)
                                                   .toList()));
    }
}
