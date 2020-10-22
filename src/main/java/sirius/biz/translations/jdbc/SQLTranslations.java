/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.jdbc;

import sirius.biz.protocol.JournalData;
import sirius.biz.protocol.Journaled;
import sirius.biz.translations.BasicTranslations;
import sirius.biz.translations.Translation;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.sql.SQLException;
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
    protected void removeTranslations() {
        try {
            oma.deleteStatement(SQLTranslation.class)
               .where(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
               .executeUpdate();
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to remove translations after deletion of %s",
                                                    owner.getUniqueName())
                            .handle();
        }
    }

    @Override
    protected void updateTranslation(SQLTranslation translation) {
        oma.update(translation);
    }

    @Override
    public void deleteText(@Nonnull Mapping field, String lang) {
        try {
            oma.deleteStatement(SQLTranslation.class)
               .where(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
               .where(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
               .where(Translation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
               .executeUpdate();

            if (owner instanceof Journaled) {
                JournalData.addJournalEntry(owner,
                                            "Deleted translated text for " + field.getName() + " (" + lang + ")");
            }
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete a translation text for %s: %s (%s)",
                                                    owner.getUniqueName(),
                                                    field.getName(),
                                                    lang)
                            .handle();
        }
    }

    @Override
    public void deleteAllTexts(@Nonnull Mapping field) {
        try {
            oma.deleteStatement(SQLTranslation.class)
               .where(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
               .where(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
               .executeUpdate();

            if (owner instanceof Journaled) {
                JournalData.addJournalEntry(owner, "Deleted all translated texts for " + field.getName());
            }
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete translation texts for %s: %s",
                                                    owner.getUniqueName(),
                                                    field.getName())
                            .handle();
        }
    }

    @Override
    protected SQLTranslation findOrCreateTranslation(@Nonnull Mapping field, String lang, String text) {
        if (!isSupportedLanguage(lang)) {
            throw new IllegalArgumentException(
                    "lang must be a language code supported by the system (supportedLanguages)!");
        }

        Optional<SQLTranslation> translation = fetchTranslation(field, lang);

        if (translation.isPresent()) {
            return translation.get();
        } else {
            SQLTranslation sqlTranslation = new SQLTranslation();
            sqlTranslation.getTranslationData().setOwner(owner.getUniqueName());
            sqlTranslation.getTranslationData().setField(field.getName());
            sqlTranslation.getTranslationData().setLang(lang);
            return sqlTranslation;
        }
    }

    @Override
    protected Optional<SQLTranslation> fetchTranslation(@Nonnull Mapping field, String lang) {
        return oma.select(SQLTranslation.class)
                  .eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
                  .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                  .eq(Translation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
                  .first();
    }

    @Override
    protected List<SQLTranslation> fetchAllTranslations(@Nonnull Mapping field) {
        return oma.select(SQLTranslation.class)
                  .eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
                  .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                  .queryList();
    }
}
