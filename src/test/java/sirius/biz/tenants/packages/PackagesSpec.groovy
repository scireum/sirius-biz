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
import sirius.web.security.UserContext
import sirius.web.security.UserInfo

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

    def "test hasRequiredPermissionForRole for current user"() {
        setup:
        UserContext.get()
                   .setCurrentUser(UserInfo.Builder.withUser(UserInfo.NOBODY).withPermissions(permissions).build())
        expect:
        packages.hasRequiredPermissionForRole(scope, role) == expectedResult
        where:
        scope          | role     | expectedResult | permissions
        "test-scope-1" | "role2"  | true           | [] as Set<String>
        "test-scope-1" | "role1a" | false          | [] as Set<String>
        "test-scope-1" | "role1b" | false          | [] as Set<String>
        "test-scope-1" | "role1a" | true           | ["permission1"] as Set<String>
        "test-scope-1" | "role1b" | true           | ["permission1"] as Set<String>
        "test-scope-1" | "role1b" | false          | ["permission2"] as Set<String>
    }
}
