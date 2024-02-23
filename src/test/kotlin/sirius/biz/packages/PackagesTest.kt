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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.util.stream.Stream
import kotlin.test.assertEquals

/**
 * Tests the [Packages] class.
 */
@ExtendWith(SiriusExtension::class)
class PackagesTest {

    @Test
    fun `Get packages for different scopes`() {
        val scope1 = packages.getPackages("test-scope-1")
        val scope2 = packages.getPackages("test-scope-2")
        assertEquals(listOf("package1", "package2"), scope1)
        assertEquals(listOf("package3"), scope2)
    }

    @Test
    fun `Get upgrades for different scopes`() {
        val scope1 = packages.getUpgrades("test-scope-1")
        val scope2 = packages.getUpgrades("test-scope-2")
        assertEquals(listOf("upgradeA", "upgradeB", "upgradeC"), scope1)
        assertEquals(listOf("upgradeD", "upgradeE"), scope2)
    }

    @ParameterizedTest
    @MethodSource("provideTestRoles")
    fun `Check hasRequiredPermissionForRole for current user`(
            role: String,
            expectedResult: Boolean,
            permissions: Set<String>
    ) {
        assertEquals(
                expectedResult,
                packages.hasRequiredPermissionForPermission(role) { key -> permissions.contains(key) }
        )
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var packages: Packages

        @JvmStatic
        fun provideTestRoles(): Stream<Arguments> = Stream.of(
                Arguments.of("role2", true, setOf<String>()),
                Arguments.of("role1a", false, setOf<String>()),
                Arguments.of("role1b", false, setOf<String>()),
                Arguments.of("role1a", true, setOf("permission1")),
                Arguments.of("role1b", true, setOf("permission1")),
                Arguments.of("role1b", false, setOf("permission2")),
        )
    }
}
