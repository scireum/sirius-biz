/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.mongo;

import sirius.biz.protocol.JournalData;
import sirius.biz.protocol.Journaled;
import sirius.biz.translations.BasicTranslations;
import sirius.biz.translations.Translation;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mongo.Mongo;
import sirius.kernel.di.std.Part;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * Stores translations into the appropriate collections of the underlying MongoDB.
 */
public class MongoTranslations extends BasicTranslations<MongoTranslation> {

    public MongoTranslations(BaseEntity<?> owner) {
        super(owner);
    }

    @Part
    private static Mongo mongo;

    @Override
    protected void removeTranslations() {
        mongo.delete()
             .where(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
             .manyFrom(MongoTranslation.class);
    }

    @Override
    protected void updateTranslation(MongoTranslation translation) {
        mango.update(translation);
    }

    @Override
    public void deleteText(@Nonnull Mapping field, String lang) {
        mongo.delete()
             .where(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
             .where(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
             .where(Translation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
             .singleFrom(MongoTranslation.class);
        if (owner instanceof Journaled) {
            JournalData.addJournalEntry(owner, "Deleted translated text for " + field.getName() + " (" + lang + ")");
        }
    }

    @Override
    public void deleteAllTexts(@Nonnull Mapping field) {
        mongo.delete()
             .where(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
             .where(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
             .manyFrom(MongoTranslation.class);
        if (owner instanceof Journaled) {
            JournalData.addJournalEntry(owner, "Deleted all translated texts for " + field.getName());
        }
    }

    @Override
    protected MongoTranslation findOrCreateTranslation(@Nonnull Mapping field, String lang, String text) {
        if (!isSupportedLanguage(lang)) {
            throw new IllegalArgumentException(
                    "lang must be a language code supported by the system (supportedLanguages)!");
        }

        Optional<MongoTranslation> translation = fetchTranslation(field, lang);

        if (translation.isPresent()) {
            return translation.get();
        } else {
            MongoTranslation mongoTranslation = new MongoTranslation();
            mongoTranslation.getTranslationData().setOwner(owner.getUniqueName());
            mongoTranslation.getTranslationData().setField(field.getName());
            mongoTranslation.getTranslationData().setLang(lang);
            return mongoTranslation;
        }
    }

    @Override
    protected Optional<MongoTranslation> fetchTranslation(@Nonnull Mapping field, String lang) {
        return mango.select(MongoTranslation.class)
                    .eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
                    .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                    .eq(Translation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
                    .first();
    }

    @Override
    protected List<MongoTranslation> fetchAllTranslations(@Nonnull Mapping field) {
        return mango.select(MongoTranslation.class)
                    .eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), owner.getUniqueName())
                    .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                    .queryList();
    }
}
