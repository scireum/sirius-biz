/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.Tags
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part
import sirius.web.resources.Resources
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Provides a simple test for the communication with the [Jupiter] repository.
 */
@Tag(Tags.NIGHTLY)
@ExtendWith(SiriusExtension::class)
class JupiterTest {

    @Test
    fun `LRU get, put & remove works`() {
        val cache = jupiter.getDefault().lru("test")
        cache.put("test", "value")
        assertEquals("value", cache.get("test").get())
        assertEquals("value", cache.extendedGet("test") { null })
        assertEquals("value1", cache.extendedGet("test1") { "value1" })
        cache.remove("test")
        assertFalse { cache.get("test").isPresent }
    }

    @Test
    fun `IDB show_tables works`() {
        val list = jupiter.getDefault().idb().showTables()
        assertEquals(1, list.size)
    }

    @Test
    fun `IDB queries work`() {
        val table = jupiter.getDefault().idb().table("countries")
        assertEquals(2, table.query().count())
        assertEquals(1, table.query().searchPaths("name").searchValue("Deutschland").count())
        assertEquals(1, table.query().searchPaths("name").searchValue("deutschland").count())
        assertEquals(0, table.query().searchPaths("name").searchValue("xxx").count())
        assertEquals(1, table.query().lookupPaths("code").searchValue("D").count())
        assertEquals(0, table.query().lookupPaths("code").searchValue("X").count())
        assertEquals(
                "D",
                table.query().lookupPaths("name").searchValue("Deutschland").singleRow("code").get().at(0).asString()
        )
        assertEquals(
                "D", table.query().lookupPaths("code").searchValue("D").singleRow("code").get().at(0).asString()
        )

        val row = table.query().searchInAllFields().searchValue("de").translate("de").singleRow("code", "name").get()
        assertEquals("D", row.at(0).asString())
        assertEquals("Deutschland", row.at(1).asString())

        assertEquals(
                "Deutschland", table.query().searchInAllFields().searchValue("de").translate("de").allRows("name").map {
            it.at(0).asString()
        }.collect(Collectors.joining(","))
        )
        assertEquals(
                "Deutschland,Österreich", table.query().translate("de").allRows("name").map {
            it.at(0).asString()
        }.collect(Collectors.joining(","))
        )
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var jupiter: Jupiter

        @Part
        @JvmStatic
        private lateinit var resources: Resources

        // In case this test starts too early, Jupiter might not have processed its repository contents.
        // We therefore give it some time to do so.
        @BeforeAll
        @JvmStatic
        fun setup() {
            jupiter.getDefault().updateConfig(resources.resolve("jupiter-test/settings.yml").get().contentAsString)
            jupiter.getDefault().repository()
                    .store("/countries.yml", resources.resolve("jupiter-test/countries.yml").get().contentAsString)
            jupiter.getDefault().repository().store(
                    "/loaders/countries.yml",
                    resources.resolve("jupiter-test/countries_loader.yml").get().contentAsString
            )
            var attempts = 10
            while (attempts-- > 0) {
                Wait.seconds(1.0)
                if (jupiter.getDefault().idb().showTables().size > 0) {
                    return
                }
            }
            throw IllegalStateException("Jupiter did not process the repository contents in time.")
        }
    }
}
