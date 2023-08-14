/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import sirius.biz.codelists.jdbc.SQLCodeLists
import sirius.biz.tenants.TenantsHelper
import sirius.db.jdbc.OMA
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part

class SQLMultiLanguageStringPropertySpec extends BaseSpecification {

    @Part
    private static OMA oma

    @Part
    private static SQLCodeLists codeLists

    def "store retrieve and validate"() {
        given:
        TenantsHelper.installTestTenant()
        def entity = new SQLMultiLanguageStringEntity()
        entity.getMultiLangText().addText("de", "Schmetterling")
        entity.getMultiLangText().addText("en", "Butterfly")
        oma.update(entity)

        when:
        def output = oma.refreshOrFail(entity)

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
        CallContext.getCurrent().setLanguage("en")

        then:
        output.getMultiLangText().fetchText() == "Butterfly"
        output.getMultiLangText().getText() == Optional.of("Butterfly")

        when:
        CallContext.getCurrent().setLanguage("fr")

        then:
        output.getMultiLangText().fetchText() == null
        output.getMultiLangText().getText() == Optional.empty()
    }


}
