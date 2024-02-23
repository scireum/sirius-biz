/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Strings
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [JobConfigData] class.
 */
@ExtendWith(SiriusExtension::class)
class JobConfigDataTest {

    @Test
    fun `string configs are stored correctly`() {
        val data = JobConfigData()
        data.configMap["foo"] = listOf("bar")
        data.updateConfig()
        assertEquals("{\"foo\":\"bar\"}", data.configuration)
    }

    @Test
    fun `string configs are read correctly`() {
        val data = JobConfigData()
        data.configuration = "{\"foo\":\"bar\"}"
        assertEquals(listOf("bar"), data.configMap["foo"])
        assertEquals("bar", data.fetchParameter("foo").get())
        assertEquals("bar", data.asParameterContext()["foo"])
    }

    @Test
    fun `array configs are stored correctly`() {
        val data = JobConfigData()
        data.configMap["foo"] = listOf("bar1", "bar2")
        data.updateConfig()
        assertEquals("{\"foo\":[\"bar1\",\"bar2\"]}", data.configuration)
    }

    @Test
    fun `array configs are read correctly`() {
        val data = JobConfigData()
        data.configuration = "{\"foo\":[\"bar1\",\"bar2\"]}"
        assertEquals(listOf("bar1", "bar2"), data.configMap["foo"])
        assertEquals(listOf("bar1", "bar2"), data.fetchParameter("foo").get())
        assertEquals("bar1|bar2", data.asParameterContext()["foo"])
    }

    @Test
    fun `null configs are stored correctly`() {
        val data = JobConfigData()
        data.configMap["foo"] = listOf(null)
        data.updateConfig()
        assertEquals("{\"foo\":null}", data.configuration)
    }

    @Test
    fun `null configs are read correctly`() {
        val data = JobConfigData()
        data.configuration = "{\"foo\":null}"
        assertTrue(data.configMap["foo"]!!.isEmpty())
        assertEquals("", data.fetchParameter("foo").get())
        assertTrue(Strings.isEmpty(data.asParameterContext()["foo"]))
    }

    @Test
    fun `empty strings are stored as null`() {
        val data = JobConfigData()
        data.configMap["foo"] = listOf("")
        data.updateConfig()
        assertEquals("{\"foo\":null}", data.configuration)
    }

    @Test
    fun `missing config keys are supported`() {
        val data = JobConfigData()
        data.configuration = "{}"
        assertNull(data.configMap["foo"])
        assertNull(data.fetchParameter("foo").get())
        assertTrue(Strings.isEmpty(data.asParameterContext()["foo"]))
    }
}
