/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.jdbc;

import sirius.biz.translations.BasicTranslations;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;

import java.util.Optional;

/**
 * Stores translations into the appropriate collections of the underlying JDBC database.
 */
public class SQLTranslations extends BasicTranslations<SQLTranslation> {

    public SQLTranslations(BaseEntity<?> owner) {
        super(owner);
    }

    @Override
    public void updateText(Mapping field, String lang, String text) {
        SQLTranslation translation = findOrCreateTranslation(field, lang, text);

        if (Strings.isEmpty(text)) {
            //TODO: delete translation if text is empty
            return;
        }

        translation.getTranslationData().setText(text);

        oma.update(translation);
    }

    @Override
    protected SQLTranslation findOrCreateTranslation(Mapping field, String lang, String text) {
        Optional<SQLTranslation> translation = fetchTranslation(field, lang);

        if (translation.isPresent()) {
            return translation.get();
        } else {
            SQLTranslation sqlTranslation = new SQLTranslation();
            sqlTranslation.setOwner(owner.getUniqueName());
            sqlTranslation.getTranslationData().setField(field.getName());
            sqlTranslation.getTranslationData().setLang(lang);
            return sqlTranslation;
        }
    }

    @Override
    protected Optional<SQLTranslation> fetchTranslation(Mapping field, String lang) {
        return Optional.ofNullable(oma.select(SQLTranslation.class)
                                      .eq(SQLTranslation.OWNER, owner.getUniqueName())
                                      .eq(SQLTranslation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                                      .eq(SQLTranslation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
                                      .queryFirst());
    }
}
