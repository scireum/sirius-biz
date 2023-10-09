/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sirius.kernel.commons.Amount
import sirius.kernel.commons.NumberFormat
import sirius.kernel.commons.Value

/**
 * Tests the [AmountRangeCheck] class.
 */
class AmountRangeCheckTest {

    @Test
    fun `values below the inclusive lower limit are correctly marked as invalid`() {
        assertThrows<IllegalArgumentException> {
            AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).perform(Value.of(0))
        }
    }

    @Test
    fun `values equal to the inclusive lower limit are correctly marked as valid`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).perform(Value.of(1))
    }

    @Test
    fun `values above the inclusive lower limit are correctly marked as valid`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).perform(Value.of(2))
    }

    @Test
    fun `values below the exclusive lower limit are correctly marked as invalid`() {
        assertThrows<IllegalArgumentException> {
            AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitExclusive(Amount.ONE)
                    .perform(Value.of(0))
        }
    }

    @Test
    fun `values equal to the exclusive lower limit are correctly marked as invalid`() {
        assertThrows<IllegalArgumentException> {
            AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitExclusive(Amount.ONE)
                    .perform(Value.of(1))
        }
    }

    @Test
    fun `values above the exclusive lower limit are correctly marked as valid`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitExclusive(Amount.ONE).perform(Value.of(2))
    }

    @Test
    fun `values above the inclusive upper limit are correctly marked as invalid`() {
        assertThrows<IllegalArgumentException> {
            AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitInclusive(Amount.ONE_HUNDRED)
                    .perform(Value.of(101))
        }
    }

    @Test
    fun `values equal to the inclusive upper limit are correctly marked as valid`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitInclusive(Amount.ONE_HUNDRED)
                .perform(Value.of(100))
    }

    @Test
    fun `values below the inclusive upper limit are correctly marked as valid`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitInclusive(Amount.ONE_HUNDRED)
                .perform(Value.of(99))
    }

    @Test
    fun `values above the exclusive upper limit are correctly marked as invalid`() {
        assertThrows<IllegalArgumentException> {
            AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitExclusive(Amount.ONE_HUNDRED)
                    .perform(Value.of(101))
        }
    }

    @Test
    fun `values equal to the exclusive upper limit are correctly marked as invalid`() {
        assertThrows<IllegalArgumentException> {
            AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitExclusive(Amount.ONE_HUNDRED)
                    .perform(Value.of(100))
        }
    }

    @Test
    fun `values below the exclusive upper limit are correctly marked as valid`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitExclusive(Amount.ONE_HUNDRED)
                .perform(Value.of(99))
    }

    @Test
    fun `empty values are ignored`() {
        AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE)
                .withUpperLimitExclusive(Amount.ONE_HUNDRED).perform(Value.of(Amount.NOTHING))
    }
}
