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
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class MongoTranslationsSpec extends BaseSpecification {

    @Part
    private static Mango mango

    private static final DESCRIPTION_TEXT = "This is a test"
    private static final TRANSLATED_TEXT = "Das ist ein Test"

    private static MongoTranslation mongoTranslation
    private static MongoTranslatableTestEntity mongoTranslatable

    def setupSpec() {
        mongoTranslatable = new MongoTranslatableTestEntity()
        mongoTranslatable.setDescription(DESCRIPTION_TEXT)
        mango.update(mongoTranslatable)
    }

    def "translating a field of a mongo entity works"() {
        given:
        mongoTranslatable.
                getTranslations().
                updateText(MongoTranslatableTestEntity.DESCRIPTION, "deu", TRANSLATED_TEXT)
        when:
        MongoTranslation translation = mango.select(MongoTranslation.class).queryOne()
        then:
        translation != null
    }

    def "get mongo translated text works"() {
        given:
        mongoTranslatable.
                getTranslations().
                updateText(MongoTranslatableTestEntity.DESCRIPTION, "deu", TRANSLATED_TEXT)
        when:
        String translatedText = mongoTranslatable.
                getTranslations().
                getText(MongoTranslatableTestEntity.DESCRIPTION, "deu")
        then:
        translatedText == TRANSLATED_TEXT
    }
}
