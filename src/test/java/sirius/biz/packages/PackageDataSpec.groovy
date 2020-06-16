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
        def packageData = new PackageData(null, null)
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        and:
        def permissions = packageData.computeExpandedPermissions()
        then:
        permissions.contains("permission1")
        permissions.contains("permission2")
    }

    def "test hasPermission returns false for permission revoked by revoked permissions"() {
        when:
        def packageData = new PackageData(null, null)
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        packageData.getRevokedPermissions().add("permission2")
        and:
        def permissions = packageData.computeExpandedPermissions()
        then:
        permissions.contains("permission1")
        !permissions.contains("permission2")
    }

    def "test hasPermission returns true for permission granted by additional permissions"() {
        when:
        def packageData = new PackageData(null, null)
        packageData.getAdditionalPermissions().add("permission3")
        and:
        def permissions = packageData.computeExpandedPermissions()
        then:
        permissions.contains("permission3")
    }

    def "test hasPermission returns false for not granted permissions"() {
        when:
        def packageData = new PackageData(null, null)
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        packageData.getAdditionalPermissions().add("permission3")
        and:
        def permissions = packageData.computeExpandedPermissions()
        then:
        !permissions.contains("not-granted-permission")
    }

    def "test hasPermission returns true for permission granted by package"() {
        when:
        def packageData = new PackageData(null, null)
        packageData.setPackage("packageBasic") // grants permission3
        and:
        def permissions = packageData.computeExpandedPermissions()
        then:
        permissions.contains("permission3")
    }

    def "test hasPermission for complex test case"() {
        when:
        def packageData = new PackageData(null, null)
        packageData.setPackage("packageBasic") // grants permission3
        packageData.getUpgrades().add("upgradeA") // grants permission1 and permission2
        packageData.getAdditionalPermissions().add("permission4")
        packageData.getRevokedPermissions().add("permission2")
        and:
        def permissions = packageData.computeExpandedPermissions()
        then:
        permissions.contains("permission1")
        !permissions.contains("permission2")
        permissions.contains("permission3")
        permissions.contains("permission4")
    }
}
