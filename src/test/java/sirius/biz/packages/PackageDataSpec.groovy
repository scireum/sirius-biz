/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages

import sirius.kernel.BaseSpecification

class PackageDataSpec extends BaseSpecification {

    def "test hasPermission returns true for permission granted by upgrades"() {
        when:
        def packageData = new PackageData()
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        then:
        packageData.hasPermission("permission1+permission2") == true
    }

    def "test hasPermission returns false for permission revoked by revoked permissions"() {
        when:
        def packageData = new PackageData()
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        packageData.getRevokedPermissions().add("permission2")
        then:
        packageData.hasPermission("permission1") == true
        packageData.hasPermission("permission2") == false
    }

    def "test hasPermission returns true for permission granted by additional permissions"() {
        when:
        def packageData = new PackageData()
        packageData.getAdditionalPermissions().add("permission3")
        then:
        packageData.hasPermission("permission3") == true
    }

    def "test hasPermission returns false for not granted permissions"() {
        when:
        def packageData = new PackageData()
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        packageData.getAdditionalPermissions().add("permission3")
        then:
        packageData.hasPermission("not-granted-permission") == false
    }

    def "test hasPermission returns true for permission granted by package"() {
        when:
        def packageData = new PackageData()
        packageData.setPackage("packageBasic") // grants permission3
        then:
        packageData.hasPermission("permission3") == true
    }

    def "test hasPermission for complex test case"() {
        when:
        def packageData = new PackageData()
        packageData.setPackage("packageBasic") // grants permission3
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        packageData.getAdditionalPermissions().add("permission4")
        packageData.getRevokedPermissions().add("permission2")
        then:
        packageData.hasPermission("permission1") == true
        packageData.hasPermission("permission2") == false
        packageData.hasPermission("permission3") == true
        packageData.hasPermission("permission4") == true
    }
}
