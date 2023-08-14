/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import sirius.biz.tenants.TenantsHelper
import sirius.db.es.Elastic
import sirius.kernel.di.std.Part

class ESMultiLanguageStringPropertySpec extends BaseSpecification {

    @Part
    private static Elastic elastic

    def "reading and writing works"() {
        given:
        TenantsHelper.installTestTenant()
        when:
        def test = new ESMultiLanguageStringEntity()
        test.getMultiLanguage().put("de", "Das ist ein Test").put("en", "This is a test")
        elastic.update(test)
        def resolved = elastic.refreshOrFail(test)
        then:
        resolved.getMultiLanguage().size() == 2
        and:
        resolved.getMultiLanguage().getText("de").get() == "Das ist ein Test"
        resolved.getMultiLanguage().getText("en").get() == "This is a test"

        when:
        resolved.getMultiLanguage().remove("de")
        and:
        elastic.update(resolved)
        and:
        resolved = elastic.refreshOrFail(test)
        then:
        resolved.getMultiLanguage().size() == 1
        and:
        !resolved.getMultiLanguage().contains("Das ist ein Test")
        resolved.getMultiLanguage().getText("en").get() == "This is a test"
    }

}
