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

/**
 * Generates a {@link FieldDefinition} for all properties for which no customer transformer exists.
 */
@Register
public class PropertyFallbackTransformer extends BaseFieldDefinitionTransformer<Property> {

    @Override
    public int getPriority() {
        return 999;
    }

    @Override
    public Class<Property> getSourceClass() {
        return Property.class;
    }

    @Override
    protected String determineType(Property property) {
        return FieldDefinition.typeOther();
    }
}
