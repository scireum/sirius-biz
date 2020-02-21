/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.db.mixing.properties.MultiLanguageStringProperty;
import sirius.db.mixing.types.StringList;
import sirius.kernel.di.std.Register;
import sirius.kernel.di.transformers.Transformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Generates a {@link StringList} from the values of a {@link MultiLanguageStringProperty}.
 */
@Register
public class MultiLanguageStringPropertyTransformer
        implements Transformer<MultiLanguageStringProperty, PrefixSearchableContentSupplier> {

    @Override
    public Class<MultiLanguageStringProperty> getSourceClass() {
        return MultiLanguageStringProperty.class;
    }

    @Override
    public Class<PrefixSearchableContentSupplier> getTargetClass() {
        return PrefixSearchableContentSupplier.class;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public PrefixSearchableContentSupplier make(@Nonnull MultiLanguageStringProperty source) {
        return entity -> {
            StringList tokens = new StringList();
            ((Map<String, String>) source.getValue(entity)).forEach((key, value) -> tokens.add(value));
            return tokens;
        };
    }
}
