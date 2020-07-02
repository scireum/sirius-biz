/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations.mongo;

import sirius.biz.translations.BasicTranslations;
import sirius.biz.translations.TranslationData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;

import java.util.Optional;

/**
 * Stores translations into the appropriate collections of the underlying MongoDB.
 */
public class MongoTranslations extends BasicTranslations<MongoTranslation> {

    public MongoTranslations(BaseEntity<?> owner) {
        super(owner);
    }

    @Override
    public void updateText(Mapping field, String lang, String text) {
        MongoTranslation translation = findOrCreateTranslation(field, lang, text);

        if (Strings.isEmpty(text)) {
            //TODO: delete translation if text is empty
            return;
        }

        translation.getTranslationData().setText(text);

        mango.update(translation);
    }

    @Override
    protected MongoTranslation findOrCreateTranslation(Mapping field, String lang, String text) {
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
        return Optional.ofNullable(mango.select(MongoTranslation.class)
                                        .eq(MongoTranslation.OWNER, owner.getUniqueName())
                                        .eq(MongoTranslation.TRANSLATION_DATA.inner(TranslationData.FIELD),
                                            field.getName())
                                        .eq(MongoTranslation.TRANSLATION_DATA.inner(TranslationData.LANG), lang)
                                        .queryFirst());
    }
}
