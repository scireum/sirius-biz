/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.annotations.Numeric;
import sirius.db.mixing.properties.AmountProperty;
import sirius.kernel.di.std.Register;

import java.util.Optional;

/**
 * Generates a {@link FieldDefinition} for an {@link AmountProperty}.
 */
@Register
public class AmountPropertyTransformer extends BaseFieldDefinitionTransformer<AmountProperty> {

    @Override
    public Class<AmountProperty> getSourceClass() {
        return AmountProperty.class;
    }

    @Override
    protected String determineType(AmountProperty property) {
        Optional<Numeric> numeric = property.getAnnotation(Numeric.class);
        return FieldDefinition.typeNumber(numeric.map(Numeric::precision).orElse(0),
                                          numeric.map(Numeric::scale).orElse(0));
    }
}
