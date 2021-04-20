/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.importer.BaseImportHandler;
import sirius.biz.importer.EntityImportHandlerExtender;
import sirius.biz.importer.ImporterContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides an extender which handles exporting {@link LookupValue} and {@link LookupValues} when using
 * {@link sirius.biz.importer.Importer importers}.
 */
@Register
public class LookupValueExtender implements EntityImportHandlerExtender {

    @Nullable
    @Override
    public <E extends BaseEntity<?>> Function<? super E, ?> createExtractor(BaseImportHandler<E> handler,
                                                                            EntityDescriptor descriptor,
                                                                            ImporterContext context,
                                                                            String fieldToExport) {
        Property property = descriptor.findProperty(fieldToExport);

        // Takes care of LookupValue fields...
        if (property instanceof LookupValueProperty) {
            LookupValueProperty lookupValueProperty = (LookupValueProperty) property;
            LookupValue referenceValue = lookupValueProperty.getReferenceValue();
            if (referenceValue.getExport() == LookupValue.Export.NAME) {
                return entity -> {
                    LookupValue lookupValue = lookupValueProperty.getLookupValue(entity);
                    return referenceValue.getTable().resolveName(lookupValue.getValue()).orElse(lookupValue.getValue());
                };
            }
        }

        // Takes care of LookupValues fields...
        if (property instanceof LookupValuesProperty) {
            LookupValuesProperty lookupValuesProperty = (LookupValuesProperty) property;
            LookupValues referenceValue = lookupValuesProperty.getReferenceValues();
            if (referenceValue.getExport() == LookupValue.Export.NAME) {
                return entity -> {
                    LookupValues lookupValues = lookupValuesProperty.getLookupValues(entity);
                    return lookupValues.data()
                                       .stream()
                                       .map(value -> referenceValue.getTable().resolveName(value).orElse(value))
                                       .collect(Collectors.joining(", "));
                };
            }
        }

        return null;
    }
}
