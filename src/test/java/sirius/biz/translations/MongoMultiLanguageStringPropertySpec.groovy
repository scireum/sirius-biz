/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import sirius.biz.tenants.TenantsHelper
import sirius.db.mongo.Mango
import sirius.db.mongo.Mongo
import sirius.kernel.BaseSpecification
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException

class MongoMultiLanguageStringPropertySpec extends BaseSpecification {

    @Part
    private static Mango mango

    @Part
    private static Mongo mongo

    def "invalid language"() {
        given:
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangTextWithValidLanguages().addText("00", "some text")

        when:
        mango.update(entity)

        then:
        thrown(HandledException)
    }

    def "invalid language in composite"() {
        given:
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangComposite().getCompositeMultiLangTextWithValidLanguages().addText("00", "some text")

        when:
        mango.update(entity)

        then:
        thrown(HandledException)
    }

    def "invalid language in mixin"() {
        given:
        def entity = new MongoMultiLanguageStringEntityWithMixin()
        entity.as(MongoMultiLanguageStringMixin.class).getMixinMultiLangTextWithValidLanguages().addText("00", "some text")

        when:
        mango.update(entity)

        then:
        thrown(HandledException)
    }

    def "Comparing persisted data with null keys works as expected"() {
        given:
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangText().addText("de", null)
        mango.update(entity)
        when:
        entity = mango.tryRefresh(entity)
        entity.getMultiLangText().addText("de", null)
        mango.update(entity)
        then:
        noExceptionThrown()
        and:
        entity.getMultiLangText() == new MultiLanguageString()
    }

    def "store works without fallback"() {
        given:
        def entity = new MongoMultiLanguageStringRequiredNoFallbackEntity()

        entity.getMultiLangText().addText("de", "")
        mango.update(entity)

        when:
        def output = mango.refreshOrFail(entity)

        then:
        noExceptionThrown()
    }

    def "store retrieve and validate"() {
        given:
        TenantsHelper.installTestTenant()
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangText().addText("de", "Schmetterling")
        entity.getMultiLangText().addText("en", "Butterfly")
        mango.update(entity)

        when:
        def output = mango.refreshOrFail(entity)

        then:
        output.getMultiLangText().size() == 2
        output.getMultiLangText().hasText("de")
        !output.getMultiLangText().hasText("fr")
        output.getMultiLangText().fetchText("de") == "Schmetterling"
        output.getMultiLangText().fetchText("fr") == null
        output.getMultiLangText().fetchText("de", "en") == "Schmetterling"
        output.getMultiLangText().fetchText("fr", "en") == "Butterfly"
        output.getMultiLangText().fetchText("fr", "es") == null
        output.getMultiLangText().getText("de") == Optional.of("Schmetterling")
        output.getMultiLangText().getText("fr") == Optional.empty()

        when:
        CallContext.getCurrent().setLang("en")

        then:
        output.getMultiLangText().fetchText() == "Butterfly"
        output.getMultiLangText().getText() == Optional.of("Butterfly")

        when:
        CallContext.getCurrent().setLang("fr")

        then:
        output.getMultiLangText().fetchText() == null
        output.getMultiLangText().getText() == Optional.empty()
    }

    def "store using default language"() {
        given:
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().setLang("en")
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangText().addText("Butterfly")
        mango.update(entity)

        when:
        def output = mango.refreshOrFail(entity)

        then:
        output.getMultiLangText().fetchText() == "Butterfly"
        output.getMultiLangText().fetchText("de") == null
    }

    def "raw data check"() {
        given:
        TenantsHelper.installTestTenant()
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangText().addText("pt", "Borboleta")
        entity.getMultiLangText().addText("es", "Mariposa")
        entity.getMultiLangText().addText("en", "")
        entity.getMultiLangText().addText("de", null)
        mango.update(entity)

        when:
        def expectedString = "[Document{{lang=pt, text=Borboleta}}, Document{{lang=es, text=Mariposa}}]"
        def storedString = mongo.find()
                                .where("id", entity.getId())
                                .singleIn("mongomultilanguagestringentity")
                                .get()
                                .get("multiLangText")
                                .asString()

        then:
        expectedString == storedString
    }

    def "fallback can not be added to field without fallback enabled"() {
        given:
        def entity = new MongoMultiLanguageStringEntity()
        when:
        entity.getMultiLangText().setFallback("test")

        then:
        thrown(IllegalStateException)
    }

    def "fallback can be added and retrieved"() {
        given:
        TenantsHelper.installTestTenant()
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangTextWithFallback().addText("de", "In Ordnung")
        entity.getMultiLangTextWithFallback().addText("en", "Fine")
        entity.getMultiLangTextWithFallback().setFallback("OK")
        mango.update(entity)

        when:
        def output = mango.refreshOrFail(entity)

        then:
        output.getMultiLangTextWithFallback().size() == 3
        output.getMultiLangTextWithFallback().hasText("de")
        output.getMultiLangTextWithFallback().hasText("en")
        output.getMultiLangTextWithFallback().hasFallback()
        !output.getMultiLangTextWithFallback().hasText("fr")

        output.getMultiLangTextWithFallback().fetchTextOrFallback("de") == "In Ordnung"
        output.getMultiLangTextWithFallback().fetchTextOrFallback("en") == "Fine"
        output.getMultiLangTextWithFallback().fetchTextOrFallback("fr") == "OK"

        output.getMultiLangTextWithFallback().fetchText("de") == "In Ordnung"
        output.getMultiLangTextWithFallback().fetchText("fr") == null

        output.getMultiLangTextWithFallback().getText("de") == Optional.of("In Ordnung")
        output.getMultiLangTextWithFallback().getText("fr") == Optional.of("OK")

        when:
        CallContext.getCurrent().setLang("en")

        then:
        output.getMultiLangTextWithFallback().fetchText() == "Fine"
        output.getMultiLangTextWithFallback().getText() == Optional.of("Fine")

        when:
        CallContext.getCurrent().setLang("fr")

        then:
        output.getMultiLangTextWithFallback().fetchTextOrFallback() == "OK"
        output.getMultiLangTextWithFallback().getText() == Optional.of("OK")
    }

    def "new null values are not stored"() {
        given:
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().setLang("en")
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangTextWithFallback().addText(null)
        entity.getMultiLangTextWithFallback().addText("de", "Super")
        entity.getMultiLangTextWithFallback().setFallback(null)
        mango.update(entity)

        when:
        def output = mango.refreshOrFail(entity)

        then:
        output.getMultiLangTextWithFallback().fetchText() == null
        output.getMultiLangTextWithFallback().fetchText("en") == null
        output.getMultiLangTextWithFallback().fetchText("de") == "Super"
        output.getMultiLangTextWithFallback().fetchText("fallback") == null
    }

    def "keys with null values are removed from the underlying map if a key already exists"() {
        given:
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().setLang("en")
        def entity = new MongoMultiLanguageStringEntity()
        entity.getMultiLangText().addText("en", "Super")
        mango.update(entity)

        when:
        entity = mango.refreshOrFail(entity)

        then:
        entity.getMultiLangText().fetchText() == "Super"

        when:
        entity.getMultiLangText().addText("en", null)
        mango.update(entity)
        def output = mango.refreshOrFail(entity)

        then:
        output.getMultiLangTextWithFallback().fetchText() == null
    }

    def "asserts setData removes null keys before persisting"() {
        given:
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().setLang("en")
        def entity = new MongoMultiLanguageStringEntity()
        Map<String, String> data = new LinkedHashMap<>()
        data.put("en", "Great")
        data.put("de", null)
        entity.getMultiLangText().setData(data)
        mango.update(entity)

        when:
        def output = mango.refreshOrFail(entity)

        then:
        output.getMultiLangText().fetchText("en") == "Great"
        output.getMultiLangText().fetchText("de") == null
        output.getMultiLangText().original().size() == 1
    }

    def "trying to directly call modify should throw an unsupported operation exception"() {
        given:
        def entity = new MongoMultiLanguageStringEntity()

        when:
        entity.getMultiLangText().modify().put("de", null)

        then:
        thrown(UnsupportedOperationException)
    }
}
