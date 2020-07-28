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

class MongoTranslationsSpec extends TranslationsSpec {

    @Part
    private static Mango mango

    private static final DESCRIPTION_FIELD = MongoTranslatableTestEntity.DESCRIPTION

    private static MongoTranslatableTestEntity mongoTranslatable

    def setupSpec() {
        mongoTranslatable = new MongoTranslatableTestEntity()
        mongoTranslatable.setDescription(DESCRIPTION_TEXT)
        mango.update(mongoTranslatable)
    }

    def cleanup() {
        List<MongoTranslation> translations = mango.select(MongoTranslation.class).queryList()
        for (MongoTranslation t : translations) {
            mango.delete(t)
        }
    }

    def "translating a field of a mongo entity works"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        List<MongoTranslation> translations = mango.select(MongoTranslation.class).queryList()
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
        List<MongoTranslation> translations = mango.select(MongoTranslation.class).queryList()
        then:
        translations.size() == 0
    }

    def "updating translation with empty text deletes it"() {
        given:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        mongoTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, "")
        and:
        List<MongoTranslation> translations = mango.select(MongoTranslation.class).queryList()
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
        List<MongoTranslation> translations = mango.select(MongoTranslation.class).queryList()
        then:
        translations.size() == 0
    }
}
