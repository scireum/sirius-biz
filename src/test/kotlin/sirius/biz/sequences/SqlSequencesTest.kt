/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences

import org.junit.jupiter.api.BeforeAll
import sirius.kernel.di.Injector

class SQLSequencesTest : SequencesTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup(): Unit {
            sequences.javaClass.getDeclaredField("sequenceStrategy").apply {
                isAccessible = true
                set(sequences, Injector.context().getPart("sql", SequenceStrategy::class.java))
            }
        }
    }

}
