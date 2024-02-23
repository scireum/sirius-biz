/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.assertEquals

@ExtendWith(SiriusExtension::class)
open class SequencesTest {

    @Test
    fun `A new sequence is automatically created`() {
        val id = "__generated" + ThreadLocalRandom.current().nextInt()
        assertEquals(1, sequences.generateId(id))
        assertEquals(2, sequences.generateId(id))
    }

    @Test
    fun `A sequence is incremented by generateId`() {
        val id = "__generated" + ThreadLocalRandom.current().nextInt()
        val value = sequences.generateId(id)
        val value1 = sequences.generateId(id)
        assertEquals(value, value1 - 1)
    }

    @Test
    fun `A new next value can be set`() {
        val id = "__generated" + ThreadLocalRandom.current().nextInt()
        sequences.setNextValue(id, 1000, false)
        assertEquals(1000, sequences.generateId(id))
    }

    @Test
    fun `Cannot be set to a lower value`() {
        val id = "__generated" + ThreadLocalRandom.current().nextInt()
        val value = sequences.generateId(id)
        sequences.generateId(id)
        assertThrows<HandledException> {
            sequences.setNextValue(id, value, false)
        }
    }

    @Test
    fun `Can be set to a lower value via force mode`() {
        val id = "__generated" + ThreadLocalRandom.current().nextInt()
        val value = sequences.generateId(id)
        sequences.generateId(id)
        sequences.setNextValue(id, value, true)
    }

    companion object {
        @Part
        @JvmStatic
        internal lateinit var sequences: Sequences
    }

}
