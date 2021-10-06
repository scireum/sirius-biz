/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.db.mixing.properties.StringMapProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Invokes the provided method to tokenize words for the key+value pairs in a {@link StringMapProperty}.
 */
@Register
public class StringMapPropertyTransformer implements Transformer<StringMapProperty, PrefixSearchableContentComputer> {

    @Override
    public Class<StringMapProperty> getSourceClass() {
        return StringMapProperty.class;
    }

    @Override
    public Class<PrefixSearchableContentComputer> getTargetClass() {
        return PrefixSearchableContentComputer.class;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public PrefixSearchableContentComputer make(@Nonnull StringMapProperty source) {
        return (entity, consumer) -> {
            ((Map<String, String>) source.getValue(entity)).forEach((key, value) -> {
                consumer.accept(key);
                consumer.accept(value);
            });
        };
    }
}
