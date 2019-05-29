/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.packages

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class PackagesSpec extends BaseSpecification {

    @Part
    static Packages packages

    def "get packages for diffrent scopes"() {
        when:
        def scope1 = packages.getPricePackages("test-scope-1")
        def scope2 = packages.getPricePackages("test-scope-2")
        then:
        scope1 == ["package1", "package2"]
        scope2 == ["package3"]
    }

    def "test getPackageName"() {
        when:
        def name = packages.getPricePackageName("test-scope-1", "package1")
        then:
        name == "Erstes Paket"
    }

    def "test getUpgradeName"() {
        when:
        def name = packages.getUpgradeName("test-scope-2", "upgradeE")
        then:
        name == "Upgrade Emil"
    }

    def "get upgrades for diffrent scopes"() {
        when:
        def scope1 = packages.getUpgrades("test-scope-1")
        def scope2 = packages.getUpgrades("test-scope-2")
        then:
        scope1 == ["upgradeA", "upgradeB", "upgradeC"]
        scope2 == ["upgradeD", "upgradeE"]
    }
}
