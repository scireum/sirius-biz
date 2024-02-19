/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.biz.codelists.LookupValuesProperty;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for a {@link LookupValuesProperty}.
 */
@Register
public class LookupValuesPropertyTransformer extends BaseFieldDefinitionTransformer<LookupValuesProperty> {

    @Override
    public Class<LookupValuesProperty> getSourceClass() {
        return LookupValuesProperty.class;
    }

    @Override
    protected String determineType(LookupValuesProperty property) {
        return FieldDefinition.typeLookupValuesProperty();
    }

    @Override
    protected void customizeField(LookupValuesProperty property, FieldDefinition field) {
        field.withTypeUrl(Strings.apply("javascript:openLookupTable('%s', '%s')",
                                        property.getReferenceValues().getTableName(),
                                        property.getReferenceValues().getTable().getTitle()));
        field.withCheck(new LookupValuesCheck(property.getReferenceValues().acceptsCustomValues()));
    }
}
