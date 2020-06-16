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

/**
 * Generates a {@link FieldDefinition} for a {@link StringProperty}.
 */
@Register
public class StringListPropertyTransformer extends BaseFieldDefinitionTransformer<StringListProperty> {

    @Override
    public Class<StringListProperty> getSourceClass() {
        return StringListProperty.class;
    }

    @Override
    protected String determineType(StringListProperty property) {
        return FieldDefinition.typeStringList();
    }
}
