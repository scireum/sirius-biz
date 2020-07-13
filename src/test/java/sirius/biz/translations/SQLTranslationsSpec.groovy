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
import sirius.kernel.di.std.Part

import java.time.Duration

class SQLTranslationsSpec extends TranslationsSpec {
    @Part
    private static OMA oma

    private static final DESCRIPTION_FIELD = SQLTranslatableTestEntity.DESCRIPTION

    private static SQLTranslatableTestEntity sqlTranslatable

    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
        sqlTranslatable = new SQLTranslatableTestEntity()
        sqlTranslatable.setDescription(DESCRIPTION_TEXT)
        oma.update(sqlTranslatable)
    }

    def cleanup() {
        List<SQLTranslation> translations = oma.select(SQLTranslation.class).queryList()
        for (SQLTranslation t : translations) {
            oma.delete(t)
        }
    }

    def "translating a field of a sql entity works"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        List<SQLTranslation> translations = oma.select(SQLTranslation.class).queryList()
        then:
        translations.size() == 1
        and:
        translations.get(0).translationData.text == GERMAN_TEXT
    }

    def "deleting a translation works"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        sqlTranslatable.getTranslations().deleteText(DESCRIPTION_FIELD, GERMAN)
        and:
        List<SQLTranslation> translations = oma.select(SQLTranslation.class).queryList()
        then:
        translations.size() == 0
    }

    def "updating translation with empty text deletes it"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, "")
        and:
        List<SQLTranslation> translations = oma.select(SQLTranslation.class).queryList()
        then:
        translations.size() == 0
    }

    def "get sql translated text works"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        when:
        Optional<String> translatedText = sqlTranslatable.getTranslations().getText(DESCRIPTION_FIELD, GERMAN)
        then:
        translatedText.isPresent()
        and:
        translatedText.get() == GERMAN_TEXT
    }

    def "get sql translated text with fallback works"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        String text = sqlTranslatable.getTranslations().getRequiredText(DESCRIPTION_FIELD, GERMAN, SWEDISH)
        then:
        text == SWEDISH_TEXT
    }

    def "get sql translated text with no matches returns default"() {
        given:
        when:
        String text = sqlTranslatable.getTranslations().getRequiredText(DESCRIPTION_FIELD, GERMAN, SWEDISH)
        then:
        text == DESCRIPTION_TEXT
    }

    def "get all texts works"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        and:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        Map<String, String> translations = sqlTranslatable.getTranslations().getAllTexts(DESCRIPTION_FIELD)
        then:
        translations.size() == 2
    }

    def "delete all texts works"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        and:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        sqlTranslatable.getTranslations().deleteAllTexts(DESCRIPTION_FIELD)
        then:
        sqlTranslatable.getTranslations().getAllTexts(DESCRIPTION_FIELD).isEmpty()
    }

    def "deleting the owner entity also deletes all associated translations"() {
        given:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, GERMAN, GERMAN_TEXT)
        and:
        sqlTranslatable.getTranslations().updateText(DESCRIPTION_FIELD, SWEDISH, SWEDISH_TEXT)
        when:
        oma.delete(sqlTranslatable)
        and:
        List<SQLTranslation> translations = oma.select(SQLTranslation.class).queryList()
        then:
        translations.size() == 0
    }
}
