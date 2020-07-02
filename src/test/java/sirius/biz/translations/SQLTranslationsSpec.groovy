/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import sirius.biz.translations.jdbc.SQLTranslation
import sirius.db.jdbc.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

import java.time.Duration

class SQLTranslationsSpec extends BaseSpecification {
    @Part
    private static OMA oma

    private static final DESCRIPTION_TEXT = "This is a test"
    private static final TRANSLATED_TEXT = "Das ist ein Test"

    private static SQLTranslation sqlTranslation
    private static SQLTranslatableTestEntity sqlTranslatable


    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
        sqlTranslatable = new SQLTranslatableTestEntity()
        sqlTranslatable.setDescription(DESCRIPTION_TEXT)
        oma.update(sqlTranslatable)
    }

    def "translating a field of an sql entity works"() {
        given:
        sqlTranslatable.
                getTranslations().
                updateText(SQLTranslatableTestEntity.DESCRIPTION, "deu", TRANSLATED_TEXT)
        when:
        SQLTranslation translation = oma.select(SQLTranslation.class).queryOne()
        then:
        translation != null
    }

    def "get sql translated text works"() {
        given:
        sqlTranslatable.getTranslations().updateText(SQLTranslatableTestEntity.DESCRIPTION, "deu", TRANSLATED_TEXT)
        when:
        String translatedText = sqlTranslatable.getTranslations().getText(SQLTranslatableTestEntity.DESCRIPTION, "deu")
        then:
        translatedText == TRANSLATED_TEXT
    }
}
