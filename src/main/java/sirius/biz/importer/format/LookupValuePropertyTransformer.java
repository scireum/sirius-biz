/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.biz.codelists.LookupValueProperty;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

/**
 * Generates a {@link FieldDefinition} for a {@link LookupValueProperty}.
 */
@Register
public class LookupValuePropertyTransformer extends BaseFieldDefinitionTransformer<LookupValueProperty> {

    @Override
    public Class<LookupValueProperty> getSourceClass() {
        return LookupValueProperty.class;
    }

    @Override
    protected String determineType(LookupValueProperty property) {
        return FieldDefinition.typeLookupValueProperty();
    }

    @Override
    protected void customizeField(LookupValueProperty property, FieldDefinition field) {
        field.withTypeUrl(Strings.apply("javascript:openLookupTable('%s', '%s')",
                                        property.getReferenceValue().getTableName(),
                                        property.getReferenceValue().getTable().getTitle()));
        field.withCheck(new LookupValueCheck(property.getReferenceValue().acceptsCustomValues()));
    }
}
