/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.db.mixing.properties.StringListProperty;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Invokes the provided method to tokenize words for the items in a {@link StringListProperty}.
 */
@Register
public class StringListPropertyTransformer implements Transformer<StringListProperty, PrefixSearchableContentComputer> {

    @Override
    public Class<StringListProperty> getSourceClass() {
        return StringListProperty.class;
    }

    @Override
    public Class<PrefixSearchableContentComputer> getTargetClass() {
        return PrefixSearchableContentComputer.class;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public PrefixSearchableContentComputer make(@Nonnull StringListProperty source) {
        return (entity, consumer) -> {
            ((List<String>) source.getValue(entity)).forEach(consumer);
        };
    }
}
