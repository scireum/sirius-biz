/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.biz.translations.MultiLanguageStringProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Invokes the provided method to tokenize words for the values in a {@link MultiLanguageStringProperty}.
 */
@Register
public class MultiLanguageStringPropertyTransformer
        implements Transformer<MultiLanguageStringProperty, PrefixSearchableContentComputer> {

    @Override
    public Class<MultiLanguageStringProperty> getSourceClass() {
        return MultiLanguageStringProperty.class;
    }

    @Override
    public Class<PrefixSearchableContentComputer> getTargetClass() {
        return PrefixSearchableContentComputer.class;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public PrefixSearchableContentComputer make(@Nonnull MultiLanguageStringProperty source) {
        return (entity, consumer) -> {
            ((Map<String, String>) source.getValue(entity)).forEach((key, value) -> {
                consumer.accept(value);
            });
        };
    }
}
