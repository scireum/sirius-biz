/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LocalDateProperty;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for a {@link LocalDateProperty}.
 */
@Register
public class LocalDatePropertyTransformer extends BaseFieldDefinitionTransformer<LocalDateProperty> {

    @Override
    public Class<LocalDateProperty> getSourceClass() {
        return LocalDateProperty.class;
    }

    @Override
    protected String determineType(LocalDateProperty property) {
        return FieldDefinition.typeDate();
    }
}
