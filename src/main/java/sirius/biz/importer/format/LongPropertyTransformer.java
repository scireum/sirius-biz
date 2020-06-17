/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.LongProperty;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for a {@link LongProperty}.
 */
@Register
public class LongPropertyTransformer extends BaseFieldDefinitionTransformer<LongProperty> {

    @Override
    public Class<LongProperty> getSourceClass() {
        return LongProperty.class;
    }

    @Override
    protected String determineType(LongProperty property) {
        return FieldDefinition.typeNumber(0, 0);
    }
}
