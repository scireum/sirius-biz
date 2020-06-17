/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LocalDateTimeProperty;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for a {@link LocalDateTimeProperty}.
 */
@Register
public class LocalDateTimePropertyTransformer extends BaseFieldDefinitionTransformer<LocalDateTimeProperty> {

    @Override
    public Class<LocalDateTimeProperty> getSourceClass() {
        return LocalDateTimeProperty.class;
    }

    @Override
    protected String determineType(LocalDateTimeProperty property) {
        return FieldDefinition.typeDateTime();
    }
}
