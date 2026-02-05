/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Doubles;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Enforces a given numeric scale and precision.
 * <p>
 * This check marks the given value as invalid if it's non-numeric, it consists of more decimal places than the provided scale
 * or the number of total digits exceeds the provided precision.
 * <p>
 * By default, the precision check follows the specifications of fixed point arithmetics.
 * This means that the overall number of digits is subtracted from the scale. E.g. a precision of 5 and scale of 2
 * will not permit 9999 as a value, even if only occupies 4 digits in total, the scale is inhered to the value as in 999900 which totalizes 6 digits.
 * <p>
 * This behavior can be overridden where arbitrary precision is used.
 * Here the decimal digits can float, so with a precision of 5 and scale 2, all these numbers are valid: 99999, 9999.9 and 999.99.
 *
 * @see #useArbitraryPrecision()
 */
public class AmountScaleCheck implements ValueCheck {

    private static final String PARAM_VALUE = "value";
    private static final String PARAM_PRECISION = "precision";
    private static final String PARAM_SCALE = "scale";

    private final int precision;
    private final int scale;
    private boolean arbitraryPrecision = false;

    /**
     * Creates a new check using the given precision and scale with fixed point arithmetic.
     *
     * @param precision the maximum precision of the numeric field
     * @param scale     the maximum scale of the numeric field
     */
    public AmountScaleCheck(int precision, int scale) {
        this.precision = precision;
        this.scale = scale;
    }

    /**
     * Enables arbitrary precision for this check.
     *
     * @return the current check for fluent method calls
     */
    public AmountScaleCheck useArbitraryPrecision() {
        this.arbitraryPrecision = true;
        return this;
    }

    @Override
    public void perform(Value value) {
        if (value.isEmptyString()) {
            return;
        }

        BigDecimal number = value.getBigDecimal();

        if (number == null) {
            throw new IllegalArgumentException(NLS.fmtr("AmountScaleCheck.errorMsg.notNumeric")
                                                  .set(PARAM_VALUE, value.toString())
                                                  .format());
        }

        BigDecimal rounded = new BigDecimal(number.unscaledValue(), number.scale()).setScale(scale, RoundingMode.FLOOR);
        if (number.subtract(rounded).compareTo(BigDecimal.valueOf(Doubles.EPSILON)) > 0) {
            throw new IllegalArgumentException(NLS.fmtr("AmountScaleCheck.errorMsg.scaleExceeded")
                                                  .set(PARAM_VALUE, value.toString())
                                                  .set(PARAM_SCALE, scale)
                                                  .format());
        }

        if (isPrecisionExceeded(number)) {
            throw new IllegalArgumentException(NLS.fmtr("AmountScaleCheck.errorMsg.precisionExceeded")
                                                  .set(PARAM_VALUE, value.toString())
                                                  .set(PARAM_PRECISION,
                                                       arbitraryPrecision ? precision : precision - scale)
                                                  .format());
        }
    }

    private boolean isPrecisionExceeded(BigDecimal number) {
        if (arbitraryPrecision) {
            return number.precision() > precision;
        }
        return number.compareTo(BigDecimal.valueOf(Math.pow(10, (precision - scale)))) > -1;
    }

    @Nullable
    @Override
    public String generateRemark() {
        return NLS.fmtr("AmountScaleCheck.remark").set(PARAM_PRECISION, precision).set(PARAM_SCALE, scale).format();
    }
}
