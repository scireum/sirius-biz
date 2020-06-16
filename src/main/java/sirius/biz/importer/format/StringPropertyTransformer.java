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

/**
 * Generates a {@link FieldDefinition} for a {@link StringProperty}.
 */
@Register
public class StringPropertyTransformer extends BaseFieldDefinitionTransformer<StringProperty> {

    @Override
    public Class<StringProperty> getSourceClass() {
        return StringProperty.class;
    }

    @Override
    protected String determineType(StringProperty property) {
        return FieldDefinition.typeString(property.getLength());
    }

    @Override
    protected void customizeField(StringProperty property, FieldDefinition field) {
        if (property.getLength() > 0) {
            field.withCheck(new LengthCheck(property.getLength()));
        }
    }
}
