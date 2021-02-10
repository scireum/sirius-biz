/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter

import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part
import sirius.web.resources.Resources

import java.util.stream.Collectors

/**
 * Provides a simple test for the communication with the Jupiter repository.
 */
class JupiterSpec extends BaseSpecification {

    @Part
    private static Jupiter jupiter

    @Part
    private static Resources resources

    // In case this test starts too early, Jupiter might not have processed its repository contents.
    // We therefore give it some time to do so.
    def setupSpec() {
        jupiter.getDefault().updateConfig(resources.resolve("jupiter-test/settings.yml").get().getContentAsString())
        jupiter.
                getDefault().
                repository().
                store("/countries.yml", resources.resolve("jupiter-test/countries.yml").get().getContentAsString())
        jupiter.
                getDefault().
                repository().
                store("/loaders/countries.yml", resources.resolve("jupiter-test/countries_loader.yml").get().getContentAsString())
        int attempts = 10
        while (attempts-- > 0) {
            Wait.seconds(1)
            if (jupiter.getDefault().idb().showTables().size() > 0) {
                return
            }
        }
    }


    def "LRU get/put/remove works"() {
        given:
        def cache = jupiter.getDefault().lru("test")
        when:
        cache.put("test", "value")
        then:
        cache.get("test").get() == "value"
        and:
        cache.extendedGet("test", { ignored -> null }) == "value"
        and:
        cache.extendedGet("test1", { ignored -> "value1" }) == "value1"
        when:
        cache.remove("test")
        then:
        !cache.get("test").isPresent()
    }

    def "IDB show_tables works"() {
        when:
        def list = jupiter.getDefault().idb().showTables()
        then:
        list.size() == 1
    }

    def "IDB queries work"() {
        when:
        def table = jupiter.getDefault().idb().table("countries")
        then:
        table.query().count() == 2
        table.query().searchPaths("name").searchValue("Deutschland").count() == 1
        table.query().searchPaths("name").searchValue("deutschland").count() == 1
        table.query().searchPaths("name").searchValue("xxx").count() == 0
        table.query().lookupPaths("code").searchValue("D").count() == 1
        table.query().lookupPaths("code").searchValue("X").count() == 0
        and:
        table.query().lookupPaths("name").searchValue("Deutschland").singleRow("code").get().at(0) == "D"
        table.query().lookupPaths("code").searchValue("D").singleRow("code").get().at(0) == "D"
        when:
        def row = table.query().searchInAllFields().searchValue("de").translate("de").singleRow("code", "name").get()
        then:
        row.at(0).getString() == "D"
        row.at(1).getString() == "Deutschland"
        and:
        table.query().searchInAllFields().searchValue("de").translate("de").allRows("name").map({ r ->
            r.at(0).asString()
        }).collect(
                Collectors.joining(",")) == "Deutschland"
        and:
        table.query().translate("de").allRows("name").map({ r ->
            r.at(0).asString()
        }).collect(
                Collectors.joining(",")) == "Deutschland,Österreich"
    }

}
