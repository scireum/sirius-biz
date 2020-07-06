/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.jdbc;

import sirius.biz.translations.BasicTranslations;
import sirius.biz.translations.Translation;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;

import java.util.List;
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
            deleteText(field, lang);
            return;
        }

        translation.getTranslationData().setText(text);

        oma.update(translation);
    }

    @Override
    public void deleteText(Mapping field, String lang) {
        Optional<SQLTranslation> translation = fetchTranslation(field, lang);
        translation.ifPresent(sqlTranslation -> oma.delete(sqlTranslation));
    }

    @Override
    public void deleteAllTexts(Mapping field) {
        Optional<List<SQLTranslation>> translations = fetchAllTranslations(field);
        if (translations.isPresent()) {
            for (SQLTranslation translation : translations.get()) {
                oma.delete(translation);
            }
        }
    }

    @Override
    protected SQLTranslation findOrCreateTranslation(Mapping field, String lang, String text) {
        if (!isSupportedLanguage(lang)) {
            return null;
        }

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
        if (field == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(oma.select(SQLTranslation.class)
                                          .eq(Translation.OWNER, owner.getUniqueName())
                                          .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD),
                                              field.getName())
                                          .eq(Translation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
                                          .queryFirst());
        }
    }

    @Override
    protected Optional<List<SQLTranslation>> fetchAllTranslations(Mapping field) {
        if (field == null) {
            return Optional.empty();
        } else {
            return Optional.of(oma.select(SQLTranslation.class)
                                  .eq(Translation.OWNER, owner.getUniqueName())
                                  .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                                  .limit(supportedLanguages.size())
                                  .queryList());
        }
    }
}
