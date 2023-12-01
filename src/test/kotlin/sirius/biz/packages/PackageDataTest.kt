/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the [PackageData] class.
 */
@ExtendWith(SiriusExtension::class)
class PackageDataTest {

    @Test
    fun `test hasPermission returns true for permission granted by upgrades`() {
        val packageData = PackageData(null, null).apply {
            upgrades.add("upgradeA") // grants permission1 and permission2
        }
        val permissions = packageData.computeExpandedPermissions()
        assertTrue { permissions.contains("permission1") }
        assertTrue { permissions.contains("permission2") }
    }

    @Test
    fun `test hasPermission returns false for permission revoked by revoked permissions`() {
        val packageData = PackageData(null, null).apply {
            upgrades.add("upgradeA") // grants permission1 and permission2
            revokedPermissions.add("permission2")
        }
        val permissions = packageData.computeExpandedPermissions()
        assertTrue { permissions.contains("permission1") }
        assertFalse { permissions.contains("permission2") }
    }

    @Test
    fun `test hasPermission returns true for permission granted by additional permissions`() {
        val packageData = PackageData(null, null).apply {
            additionalPermissions.add("permission3")
        }
        val permissions = packageData.computeExpandedPermissions()
        assertTrue { permissions.contains("permission3") }
    }

    @Test
    fun `test hasPermission returns false for not granted permissions`() {
        val packageData = PackageData(null, null).apply {
            upgrades.add("upgradeA") // grants permission1 and permission2
            additionalPermissions.add("permission3")
        }
        val permissions = packageData.computeExpandedPermissions()
        assertFalse { permissions.contains("not-granted-permission") }
    }

    @Test
    fun `test hasPermission returns true for permission granted by package`() {
        val packageData = PackageData(null, null).apply {
            `package` = "packageBasic" // grants permission3
        }
        val permissions = packageData.computeExpandedPermissions()
        assertTrue { permissions.contains("permission3") }
    }

    @Test
    fun `test hasPermission for complex test case`() {
        val packageData = PackageData(null, null).apply {
            `package` = "packageBasic" // grants permission3
            upgrades.add("upgradeA") // grants permission1 and permission2
            additionalPermissions.add("permission4")
            revokedPermissions.add("permission2")
        }
        val permissions = packageData.computeExpandedPermissions()
        assertTrue { permissions.contains("permission1") }
        assertFalse { permissions.contains("permission2") }
        assertTrue { permissions.contains("permission3") }
        assertTrue { permissions.contains("permission4") }
    }
}
