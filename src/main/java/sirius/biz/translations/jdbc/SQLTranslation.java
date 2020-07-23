/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.jdbc;

import sirius.biz.translations.Translation;
import sirius.biz.translations.TranslationData;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.annotations.Index;

/**
 * Stores translations as a table.
 * <p>
 * Note that translations should only be accessed via siblings of {@link sirius.biz.translations.BasicTranslations}.
 */
@Index(name = "lookup",
        columns = {"translationData_owner", "translationData_field", "translationData_lang"},
        unique = true)
public class SQLTranslation extends SQLEntity implements Translation {
    private final TranslationData translationData = new TranslationData();

    @Override
    public TranslationData getTranslationData() {
        return translationData;
    }
}
