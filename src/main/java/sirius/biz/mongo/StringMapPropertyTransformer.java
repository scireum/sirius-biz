/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.db.mixing.properties.StringMapProperty;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Generates a {@link StringMap} from the values of a {@link StringMapProperty}.
 */
@Register
public class StringMapPropertyTransformer implements Transformer<StringMapProperty, PrefixSearchableContentSupplier> {

    @Override
    public Class<StringMapProperty> getSourceClass() {
        return StringMapProperty.class;
    }

    @Override
    public Class<PrefixSearchableContentSupplier> getTargetClass() {
        return PrefixSearchableContentSupplier.class;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public PrefixSearchableContentSupplier make(@Nonnull StringMapProperty source) {
        return entity -> {
            StringMap tokens = new StringMap();
            tokens.setData(((Map<String, String>) source.getValue(entity)));
            return tokens;
        };
    }
}
