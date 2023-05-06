/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.biz.importer.BaseImportHandler;
import sirius.biz.importer.EntityImportHandlerExtender;
import sirius.biz.importer.ImporterContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Handles importing and exporting for {@link MultiLanguageString}.
 * <p>
 * Note that the effective language to import and export can be controlled via {@link MultiLanguageStringHelper}.
 */
@Register
public class MultiLanguageStringExtender implements EntityImportHandlerExtender {

    @Override
    public void collectLoaders(BaseImportHandler<? extends BaseEntity<?>> handler,
                               EntityDescriptor descriptor,
                               ImporterContext context,
                               BiConsumer<Mapping, BiConsumer<Context, Object>> loaderCollector) {
        for (Property property : descriptor.getProperties()) {
            if (property instanceof MultiLanguageStringProperty multiLanguageStringProperty) {
                loaderCollector.accept(Mapping.named(property.getName()),
                                       (data, entity) -> load(multiLanguageStringProperty, context, data, entity));
            }
        }
    }

    private void load(MultiLanguageStringProperty property, ImporterContext context, Context data, Object entity) {
        MultiLanguageString multiLanguageString = property.getMultiLanguageString(entity);
        Value value = data.getValue(property.getName());

        // MultiLanguageStringValue can be directly applied to the target string...
        if (value.get() instanceof MultiLanguageStringHelper.MultiLanguageStringValue) {
            MultiLanguageStringHelper.MultiLanguageStringValue multiLanguageStringValue =
                    value.get(MultiLanguageStringHelper.MultiLanguageStringValue.class, null);
            if (multiLanguageStringValue.replace) {
                multiLanguageString.clear();
            }
            if (multiLanguageStringValue.data != null) {
                multiLanguageStringValue.data.forEach(multiLanguageString::addText);
            }
            return;
        }

        // Otherwise we use the value as string and applied as configured by the MultiLanguageStringHelper...
        MultiLanguageStringHelper multiLanguageStringHelper =
                context.getImporter().findHelper(MultiLanguageStringHelper.class);
        if (multiLanguageStringHelper.isReplaceOnImport()) {
            multiLanguageString.clear();
        }

        if (multiLanguageStringHelper.hasForcedLanguage()) {
            multiLanguageString.put(multiLanguageStringHelper.getForcedLanguage(), value.getString());
        } else if (multiLanguageString.isWithFallback()) {
            multiLanguageString.setFallback(value.toString());
        } else {
            multiLanguageString.put(multiLanguageStringHelper.getEffectiveLanguage(), value.getString());
        }
    }

    @Nullable
    @Override
    public <E extends BaseEntity<?>> Function<? super E, ?> createExtractor(BaseImportHandler<E> handler,
                                                                            EntityDescriptor descriptor,
                                                                            ImporterContext context,
                                                                            String fieldToExport) {
        Property property = descriptor.findProperty(fieldToExport);
        if (property instanceof MultiLanguageStringProperty multiLanguageStringProperty) {
            return entity -> extractValue(context, multiLanguageStringProperty, entity);
        }

        return null;
    }

    private <E extends BaseEntity<?>> String extractValue(ImporterContext context,
                                                          MultiLanguageStringProperty property,
                                                          E entity) {
        MultiLanguageStringHelper multiLanguageStringHelper =
                context.getImporter().findHelper(MultiLanguageStringHelper.class);
        MultiLanguageString multiLanguageString = property.getMultiLanguageString(entity);
        if (multiLanguageStringHelper.hasForcedLanguage()) {
            return multiLanguageString.fetchTextOrFallback(multiLanguageStringHelper.getForcedLanguage());
        }
        if (multiLanguageString.isWithFallback()) {
            return multiLanguageString.getFallback();
        } else {
            return multiLanguageString.fetchText(multiLanguageStringHelper.getEffectiveLanguage());
        }
    }
}
