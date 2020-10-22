/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.mongo;

import sirius.biz.translations.Translation;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.annotations.Index;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;

/**
 * Stores translations as a collection.
 * <p>
 * Note that translations should only be accessed via siblings of {@link sirius.biz.translations.BasicTranslations}.
 */
@Index(name = "lookup",
        columns = {"translationData_owner", "translationData_field", "translationData_lang"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING},
        unique = true)
public class MongoTranslation extends MongoEntity implements Translation {
    private final TranslationData translationData = new TranslationData();

    @Override
    public TranslationData getTranslationData() {
        return translationData;
    }
}
