/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixable;
import sirius.db.mixing.annotations.Mixin;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Represents an entity with a mixin to test properties of type {@link MultiLanguageString}
 */
@Mixin(MongoMultiLanguageStringEntityWithMixin.class)
public class MongoMultiLanguageStringMixin extends Mixable {
    public static final Mapping MIXIN_MULTILANGTEXT_WITH_VALID_LANGUAGES =
            Mapping.named("mixinMultiLangTextWithValidLanguages");
    @NullAllowed
    private final MultiLanguageString mixinMultiLangTextWithValidLanguages =
            new MultiLanguageString().withValidLanguages(MongoMultiLanguageStringEntity.validLanguages);

    public MultiLanguageString getMixinMultiLangTextWithValidLanguages() {
        return mixinMultiLangTextWithValidLanguages;
    }
}
