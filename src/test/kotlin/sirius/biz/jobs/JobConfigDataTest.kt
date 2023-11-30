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
import sirius.kernel.commons.Value
import kotlin.test.assertEquals

/**
 * Tests the [JobConfigData] class.
 */
@ExtendWith(SiriusExtension::class)
class JobConfigDataTest {

    @Test
    fun `string configs are stored correctly`() {
        val data = JobConfigData()
        data.configMap["foo"] = Value.of("bar")
        data.updateConfig()
        assertEquals("{\"foo\":\"bar\"}", data.configuration)
    }

    @Test
    fun `string configs are read correctly`() {
        val data = JobConfigData()
        data.configuration = "{\"foo\":\"bar\"}"
        assertEquals("bar", data.configMap["foo"].toString())
        assertEquals("bar", data.fetchParameter("foo").asString())
    }

    @Test
    fun `array configs are stored correctly`() {
        val data = JobConfigData()
        data.configMap["foo"] = Value.of(arrayOf("bar1", "bar2"))
        data.updateConfig()
        assertEquals("{\"foo\":[\"bar1\",\"bar2\"]}", data.configuration)
    }

    @Test
    fun `array configs are read correctly`() {
        val data = JobConfigData()
        data.configuration = "{\"foo\":[\"bar1\",\"bar2\"]}"
        val list = data.configMap["foo"]?.get() as List<*>
        assertEquals("bar1", list[0])
        assertEquals("bar2", list[1])
        assertEquals("bar1|bar2", data.fetchParameter("foo").asString())
    }
}
