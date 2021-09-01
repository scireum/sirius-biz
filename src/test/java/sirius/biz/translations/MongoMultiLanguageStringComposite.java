/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Represents a composite to test properties of type {@link MultiLanguageString}
 */
public class MongoMultiLanguageStringComposite extends Composite {
    public static final Mapping MIXIN_MULTILANGTEXT = Mapping.named("mixinMultiLangText");
    @NullAllowed
    private final MultiLanguageString mixinMultiLangText = new MultiLanguageString();

    public MultiLanguageString getMixinMultiLangText() {
        return mixinMultiLangText;
    }
}
