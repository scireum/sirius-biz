/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.IntegerProperty;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for an {@link IntegerProperty}.
 */
@Register
public class IntegerPropertyTransformer extends BaseFieldDefinitionTransformer<IntegerProperty> {

    @Override
    public Class<IntegerProperty> getSourceClass() {
        return IntegerProperty.class;
    }

    @Override
    protected String determineType(IntegerProperty property) {
        return FieldDefinition.typeNumber(0, 0);
    }
}
