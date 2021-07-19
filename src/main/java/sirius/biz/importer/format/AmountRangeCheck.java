/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Amount;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

/**
 * Enforces a given range for values in the associated field.
 * <p>
 * Note that this check will ignore empty amounts. To prohibit these, use a {@link RequiredCheck}.
 */
public class AmountRangeCheck implements ValueCheck {

    private static final String PARAM_VALUE = "value";
    private static final String PARAM_OPERATOR = "operator";
    private static final String PARAM_LIMIT = "limit";

    private Amount min = Amount.NOTHING;
    private boolean includeMin;
    private Amount max = Amount.NOTHING;
    private boolean includeMax;
    private final NumberFormat numberFormat;

    /**
     * Creates a new check.
     *
     * @param numberFormat the format to use when outputting numbers
     */
    public AmountRangeCheck(NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    /**
     * Adds a lower limit.
     *
     * @param min the lower limit (all values must be greater than the given value)
     * @return the check itself for fluent method calls
     */
    public AmountRangeCheck withLowerLimitExclusive(Amount min) {
        this.min = min;
        this.includeMin = false;
        return this;
    }

    /**
     * Adds a lower limit (inclusive).
     *
     * @param min the lower limit (all values must be greater than or equal to the given value)
     * @return the check itself for fluent method calls
     */
    public AmountRangeCheck withLowerLimitInclusive(Amount min) {
        this.min = min;
        this.includeMin = true;
        return this;
    }

    /**
     * Adds an upper limit.
     *
     * @param max the upper limit (all values must be less than the given value)
     * @return the check itself for fluent method calls
     */
    public AmountRangeCheck withUpperLimitExclusive(Amount max) {
        this.max = max;
        this.includeMax = false;
        return this;
    }

    /**
     * Adds an upper limit (inclusive).
     *
     * @param max the upper limit (all values must be less than or equal to the given value)
     * @return the check itself for fluent method calls
     */
    public AmountRangeCheck withUpperLimitInclusive(Amount max) {
        this.max = max;
        this.includeMax = true;
        return this;
    }

    @Override
    public void perform(Value value) {
        Amount amount = value.getAmount();
        if (amount.isEmpty()) {
            return;
        }

        if (min.isFilled()) {
            if (includeMin) {
                if (amount.isLessThan(min)) {
                    throwRangeError(amount, ">=", min);
                }
            } else if (amount.isLessThanOrEqual(min)) {
                throwRangeError(amount, ">", min);
            }
        }
        if (max.isFilled()) {
            if (includeMax) {
                if (amount.isGreaterThan(max)) {
                    throwRangeError(amount, "<=", max);
                }
            } else if (amount.isGreaterThanOrEqual(max)) {
                throwRangeError(amount, "<", max);
            }
        }
    }

    private void throwRangeError(Amount value, String operator, Amount limit) {
        throw new IllegalArgumentException(NLS.fmtr("AmountRangeCheck.errorMsg")
                                              .set(PARAM_VALUE, value.toString(numberFormat))
                                              .set(PARAM_OPERATOR, operator)
                                              .set(PARAM_LIMIT, limit.toString(numberFormat))
                                              .format());
    }

    @Override
    public String generateRemark() {
        if (min.isFilled()) {
            if (max.isFilled()) {
                return NLS.fmtr("AmountRangeCheck.minMaxRemark")
                          .set("min", min.toString(numberFormat))
                          .set("minOperator", includeMin ? ">=" : ">")
                          .set("max", max.toString(numberFormat))
                          .set("maxOperator", includeMax ? "<=" : "<")
                          .format();
            }

            return NLS.fmtr("AmountRangeCheck.remark")
                      .set(PARAM_LIMIT, min.toString(numberFormat))
                      .set(PARAM_OPERATOR, includeMin ? ">=" : ">")
                      .format();
        }

        if (max.isFilled()) {
            return NLS.fmtr("AmountRangeCheck.remark")
                      .set(PARAM_LIMIT, max.toString(numberFormat))
                      .set(PARAM_OPERATOR, includeMax ? "<=" : "<")
                      .format();
        }

        return null;
    }
}
