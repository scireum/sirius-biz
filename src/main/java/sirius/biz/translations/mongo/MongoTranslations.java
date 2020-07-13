/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.mongo;

import sirius.biz.translations.BasicTranslations;
import sirius.biz.translations.Translation;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Stores translations into the appropriate collections of the underlying MongoDB.
 */
public class MongoTranslations extends BasicTranslations<MongoTranslation> {

    public MongoTranslations(BaseEntity<?> owner) {
        super(owner);
    }

    @Override
    protected void removeTranslations() {
        mango.select(MongoTranslation.class).eq(Translation.OWNER, owner.getUniqueName()).delete();
    }

    @Override
    public void updateText(Mapping field, String lang, String text) {
        MongoTranslation translation = findOrCreateTranslation(field, lang, text);

        if (Strings.isEmpty(text)) {
            deleteText(field, lang);
            return;
        }

        translation.getTranslationData().setText(text);

        mango.update(translation);
    }

    @Override
    public void deleteText(Mapping field, String lang) {
        fetchTranslation(field, lang).ifPresent(mango::delete);
    }

    @Override
    public void deleteAllTexts(Mapping field) {
        for (MongoTranslation translation : fetchAllTranslations(field)) {
            mango.delete(translation);
        }
    }

    @Override
    protected MongoTranslation findOrCreateTranslation(Mapping field, String lang, String text) {
        if (!isSupportedLanguage(lang)) {
            return null;
        }

        Optional<MongoTranslation> translation = fetchTranslation(field, lang);

        if (translation.isPresent()) {
            return translation.get();
        } else {
            MongoTranslation mongoTranslation = new MongoTranslation();
            mongoTranslation.setOwner(owner.getUniqueName());
            mongoTranslation.getTranslationData().setField(field.getName());
            mongoTranslation.getTranslationData().setLang(lang);
            return mongoTranslation;
        }
    }

    @Override
    protected Optional<MongoTranslation> fetchTranslation(Mapping field, String lang) {
        if (field == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(mango.select(MongoTranslation.class)
                                            .eq(Translation.OWNER, owner.getUniqueName())
                                            .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD),
                                                field.getName())
                                            .eq(Translation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
                                            .queryFirst());
        }
    }

    @Override
    protected List<MongoTranslation> fetchAllTranslations(Mapping field) {
        if (field == null) {
            return Collections.emptyList();
        } else {
            return mango.select(MongoTranslation.class)
                        .eq(Translation.OWNER, owner.getUniqueName())
                        .eq(Translation.TRANSLATION_DATA.inner(TranslationData.FIELD), field.getName())
                        .limit(supportedLanguages.size())
                        .queryList();
        }
    }
}
