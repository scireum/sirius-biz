/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.mixing.Mapping;
import sirius.db.mongo.MongoEntity;

import java.util.Arrays;

/**
 * Represents an entity to test properties of type {@link MultiLanguageString}
 */
public class MongoMultiLanguageStringRequiredNoFallbackEntity extends MongoEntity {
    public static final Mapping MULTILANGTEXT = Mapping.named("multiLangText");
    private final MultiLanguageString multiLangText = new MultiLanguageString().withValidLanguages(Arrays.asList("de","en"));

    public MultiLanguageString getMultiLangText() {
        return multiLangText;
    }
}
