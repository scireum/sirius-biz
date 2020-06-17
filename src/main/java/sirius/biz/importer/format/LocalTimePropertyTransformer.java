/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LocalDateTimeProperty;
import sirius.db.mixing.properties.LocalTimeProperty;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for a {@link LocalDateTimeProperty}.
 */
@Register
public class LocalTimePropertyTransformer extends BaseFieldDefinitionTransformer<LocalTimeProperty> {

    @Override
    public Class<LocalTimeProperty> getSourceClass() {
        return LocalTimeProperty.class;
    }

    @Override
    protected String determineType(LocalTimeProperty property) {
        return FieldDefinition.typeTime();
    }
}
