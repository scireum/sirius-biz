/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import sirius.kernel.SiriusExtension
import java.util.stream.Stream

@ExtendWith(SiriusExtension::class)
class SearchableEntityTest {

    companion object {
        @JvmStatic
        fun generateTokenSplittingTestCases(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "max.mustermann@website.com",
                    listOf(
                        "com",
                        "max",
                        "max.mustermann",
                        "max.mustermann@website.com",
                        "mustermann",
                        "website",
                        "website.com"
                    )
                ),
                Arguments.of("test-foobar", listOf("foobar", "test", "test-foobar")),
                Arguments.of(
                    "test123@bla-bar.foo",
                    listOf("bar", "bla", "bla-bar.foo", "foo", "test123", "test123@bla-bar.foo")
                )
            )
        }
    }

    @Test
    fun `Tokenizing works`() {
        val entity = SearchableTestEntity()
        entity.test = "This is a test"
        entity.unsearchableTest = "Secret Content"
        entity.searchableContent =
            "email:test@test.local 12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
        entity.updateSearchField()

        val tokens = entity.searchField.split(" ").toSet()
        assertTrue(tokens.contains("this"))
        assertTrue(tokens.contains("test"))
        assertTrue(tokens.contains("email"))
        assertFalse(tokens.contains("secret"))
        assertFalse(tokens.contains("content"))
        assertTrue(tokens.contains("test"))
        assertTrue(tokens.contains("local"))
        assertFalse(
            tokens.contains(
                "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
            )
        )
    }

    @ParameterizedTest
    @MethodSource("generateTokenSplittingTestCases")
    fun `Splitting tokens works as intended`(input: String, output: List<String>) {
        val entity = SearchableTestEntity()
        entity.test = input
        entity.updateSearchField()

        assertEquals(
            output,
            entity.searchField.split(" ").filter { it.isNotEmpty() }.toMutableList().sorted(),
        )
    }
}
