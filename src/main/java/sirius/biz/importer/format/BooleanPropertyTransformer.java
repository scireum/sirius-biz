/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.db.mixing.properties.BooleanProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

/**
 * Generates a {@link FieldDefinition} for a {@link BooleanProperty}.
 */
@Register
public class BooleanPropertyTransformer extends BaseFieldDefinitionTransformer<BooleanProperty> {

    @Override
    public Class<BooleanProperty> getSourceClass() {
        return BooleanProperty.class;
    }

    @Override
    protected String determineType(BooleanProperty property) {
        return FieldDefinition.typeBoolean();
    }

    @Override
    protected void customizeField(BooleanProperty property, FieldDefinition field) {
        field.withCheck(new ValueInListCheck("true",
                                             "false",
                                             "$" + NLS.CommonKeys.YES.key(),
                                             "$" + NLS.CommonKeys.NO.key()));
    }
}
