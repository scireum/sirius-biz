/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import sirius.db.mongo.Mango
import sirius.kernel.di.Injector
import sirius.kernel.di.std.Part
import kotlin.test.assertEquals

class MongoSequencesTest : SequencesTest() {

    @Test
    fun `Saving entities with sequential IDs works`() {
        val entity1 = SequentialMongoBizEntityA()
        val entity2 = SequentialMongoBizEntityA()
        // reset sequences that may have been set by other tests
        sequences.setNextValue(entity2.typeName, 1, true)
        mango.select(SequentialMongoBizEntityA::class.java).delete()
        // new entities are created
        mango.update(entity1)
        mango.update(entity2)
        // the same sequence is used
        assertEquals("1", entity1.id)
        assertEquals("2", entity2.id)
    }

    @Test
    fun `Different type of entities use different sequences`() {
        // two distinct entity types
        val entity1 = SequentialMongoBizEntityA()
        val entity2 = SequentialMongoBizEntityB()
        // reset sequences that may have been set by other tests
        sequences.setNextValue(entity1.typeName, 1, true)
        mango.select(SequentialMongoBizEntityA::class.java).delete()
        sequences.setNextValue(entity2.typeName, 1, true)
        mango.select(SequentialMongoBizEntityB::class.java).delete()
        // new entities are created
        mango.update(entity1)
        mango.update(entity2)
        // different sequences are used
        assertEquals("1", entity1.id)
        assertEquals("1", entity2.id)
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango

        @BeforeAll
        @JvmStatic
        fun setup(): Unit {
            sequences.javaClass.getDeclaredField("sequenceStrategy").apply {
                isAccessible = true
                set(sequences, Injector.context().getPart("mongo", SequenceStrategy::class.java))
            }
        }
    }
}
