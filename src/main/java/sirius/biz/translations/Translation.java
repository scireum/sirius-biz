/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.mixing.Mapping;

/**
 * Provides constants and methods that have to be present in any Translation entity
 */
public interface Translation {
    /**
     * Contains a reference to the {@link sirius.db.mixing.Composite} with the actual translation data.
     */
    Mapping TRANSLATION_DATA = Mapping.named("translationData");

    TranslationData getTranslationData();
}
