/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.mongo.Mango
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import kotlin.test.assertEquals

@ExtendWith(SiriusExtension::class)
class IndicatorTest {

    companion object {
        @Part
        private lateinit var mango: Mango
    }

    @Test
    fun `Execute non-batch indicator`() {
        val entity = IndicatorTestEntity()
        entity.value = "foo"
        mango.update(entity)

        assertTrue(entity.indicators.indications.contains("checkvalue"))

        entity.value = "bar"
        mango.update(entity)

        assertEquals(0, entity.indicators.indications.size)
    }
}
