/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import sirius.db.mongo.Mango
import sirius.db.mongo.QueryBuilder
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the [PrefixSearchableEntity] implementation.
 */
@ExtendWith(SiriusExtension::class)
class PrefixSearchableEntityTest {

    @Test
    fun `Tokenizing works`() {
        val entity = PrefixSearchableTestEntity()
        entity.test = "This is a test"
        entity.unsearchableTest = "Secret Content"
        entity.updateSearchField()
        entity.searchPrefixes.data().apply {
            assertTrue { contains("this") }
            assertTrue { contains("is") }
            assertTrue { contains("a") }
            assertTrue { contains("test") }
            assertFalse { contains("secret") }
            assertFalse { contains("content") }
        }
    }

    @Test
    fun `Tokenizing string map works`() {
        val entity = PrefixSearchableTestEntity()
        entity.map.put("color", "Indigo blue")
        entity.map.put("shape", "Triangle")
        entity.updateSearchField()
        entity.searchPrefixes.data().apply {
            assertTrue { contains("color") }
            assertTrue { contains("blue") }
            assertTrue { contains("indigo") }
            assertTrue { contains("indigo blue") }
            assertTrue { contains("shape") }
            assertTrue { contains("triangle") }
        }
    }

    @Test
    fun `Tokenizing multi language works`() {
        val entity = PrefixSearchableTestEntity()
        entity.multiLanguageText.addText("de", "Schmetterling")
        entity.multiLanguageText.addText("en", "Nice butterfly")
        entity.updateSearchField()
        entity.searchPrefixes.data().apply {
            assertTrue { contains("schmetterling") }
            assertTrue { contains("nice") }
            assertTrue { contains("butterfly") }
            assertTrue { contains("nice butterfly") }
            assertFalse { contains("de") }
            assertFalse { contains("en") }
        }
    }

    @Test
    fun `Tokenizing string list works`() {
        val entity = PrefixSearchableTestEntity()
        entity.list.add("Grumpy Cat").add("Dog")
        entity.updateSearchField()
        entity.searchPrefixes.data().apply {
            assertTrue { contains("grumpy") }
            assertTrue { contains("cat") }
            assertTrue { contains("dog") }
            assertTrue { contains("grumpy cat") }
        }
    }

    @ParameterizedTest
    @MethodSource("provideTestEmails")
    fun `Splitting with emails works`(input: String, expectedTokens: List<String>) {
        val entity = PrefixSearchableTestEntity()
        entity.test = input
        entity.updateSearchField()
        assertEquals(expectedTokens.sorted(), entity.searchPrefixes.modify().sorted())
    }

    @Test
    fun `Searching works`() {
        val entity = PrefixSearchableTestEntity()
        entity.test = "Some Test"
        mango.update(entity)
        assertTrue {
            mango.select(PrefixSearchableTestEntity::class.java)
                    .where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "som"))
                    .first()
                    .isPresent
        }
        assertTrue {
            mango.select(PrefixSearchableTestEntity::class.java)
                    .where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "Test"))
                    .first()
                    .isPresent
        }
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango

        @JvmStatic
        fun provideTestEmails(): Stream<Arguments> = Stream.of(
                Arguments.of("a.b@c.de", listOf("a", "b", "c", "de", "a.b", "c.de", "a.b@c.de")),
                Arguments.of("a24@x.de", listOf("a24", "a", "x", "de", "x.de", "a24@x.de")),
        )
    }
}
