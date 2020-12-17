/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations


import sirius.biz.translations.mongo.MongoTranslation
import sirius.db.mongo.Mango
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException

class MongoTranslationsSpec extends TranslationsSpec {

    @Part
    private static Mango mango

    private static final DESCRIPTION_FIELD = MongoTranslatable.DESCRIPTION

    private static MongoTranslatable mongoTranslatable

    def setupSpec() {
        mongoTranslatable = new MongoTranslatable()
        mongoTranslatable.setDescription(DESCRIPTION_TEXT)
        mango.update(mongoTranslatable)
    }

    def cleanup() {
        List<MongoTranslation> translations = mango.
                select(MongoTranslation.class).
                eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), mongoTranslatable.getUniqueName()).
                queryList()
        for (MongoTranslation t : translations) {
            mango.delete(t)
        }
    }

    def "translating a field of a mongo entity works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        List<MongoTranslation> translations = mango.
                select(MongoTranslation.class).
                eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), mongoTranslatable.getUniqueName()).
                queryList()
        then:
        translations.size() == 1
        and:
        translations.get(0).translationData.text == GERMAN_TEXT
    }

    def "deleting a translation works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        mongoTranslatable.getTranslations().deleteText(DESCRIPTION_FIELD, GERMAN)
        and:
        List<MongoTranslation> translations = mango.
                select(MongoTranslation.class).
                eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), mongoTranslatable.getUniqueName()).
                queryList()
        then:
        translations.size() == 0
    }

    def "updating translation with empty text deletes it"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, "")
        and:
        List<MongoTranslation> translations = mango.
                select(MongoTranslation.class).
                eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), mongoTranslatable.getUniqueName()).
                queryList()
        then:
        translations.size() == 0
    }

    def "get mongo translated text works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        Optional<String> translatedText = mongoTranslatable.getTranslations().getText(DESCRIPTION_FIELD, GERMAN)
        then:
        translatedText.isPresent()
        and:
        translatedText.get() == GERMAN_TEXT
    }

    def "get mongo translated text with fallback works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        String text = mongoTranslatable.getTranslations().getRequiredText(DESCRIPTION_FIELD, GERMAN, SWEDISH)
        then:
        text == SWEDISH_TEXT
    }

    def "get mongo translated text with no matches returns default"() {
        given:
        when:
        String text = mongoTranslatable.getTranslations().getRequiredText(DESCRIPTION_FIELD, GERMAN, SWEDISH)
        then:
        text == DESCRIPTION_TEXT
    }

    def "get all texts works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        and:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        Map<String, String> translations = mongoTranslatable.getTranslations().getAllTexts(DESCRIPTION_FIELD)
        then:
        translations.size() == 2
    }

    def "delete all texts works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        and:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        mongoTranslatable.getTranslations().deleteAllTexts(DESCRIPTION_FIELD)
        then:
        mongoTranslatable.getTranslations().getAllTexts(DESCRIPTION_FIELD).isEmpty()
    }

    def "deleting the owner entity also deletes all associated translations"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        and:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        mango.delete(mongoTranslatable)
        and:
        List<MongoTranslation> translations = mango.
                select(MongoTranslation.class).
                eq(Translation.TRANSLATION_DATA.inner(TranslationData.OWNER), mongoTranslatable.getUniqueName()).
                queryList()
        then:
        translations.size() == 0
    }

    def "invalid language"() {
        given:
        def invalidLang = "val"
        when:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, invalidLang, GERMAN_TEXT)
        then:
        thrown(HandledException)
    }

    def "translations without specified valid languages accept all language codes"() {
        given:
        def invalidLang = "val"
        when:
        mongoTranslatable.getUnrestrictedTranslations().updateText(DESCRIPTION_FIELD, invalidLang, GERMAN_TEXT)
        and:
        def text = mongoTranslatable.getUnrestrictedTranslations().getText(DESCRIPTION_FIELD, invalidLang)
        then:
        text.isPresent()
        and:
        text.get() == GERMAN_TEXT
    }
}
