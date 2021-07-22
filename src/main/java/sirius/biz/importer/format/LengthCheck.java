/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

/**
 * Enforces a given field length.
 * <p>
 * Note, as most of the {@link Value} methods will perform an automatic <tt>trim</tt>, we also trim the contents before
 * checking the length. Use {@link #checkUntrimmed()} to suppress this behaviour.
 */
public class LengthCheck extends StringCheck {

    private final int maxLength;

    /**
     * Creates a new check which enforces the given length.
     *
     * @param maxLength the maximal length of a value in the associated field
     */
    public LengthCheck(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public void perform(Value value) {
        String effectiveValue = determineEffectiveValue(value);
        if (effectiveValue != null && effectiveValue.length() > maxLength) {
            throw new IllegalArgumentException(NLS.fmtr("LengthCheck.errorMsg")
                                                  .set("length", effectiveValue.length())
                                                  .set("maxLength", maxLength)
                                                  .format());
        }
    }

    @Override
    public String generateRemark() {
        return NLS.fmtr("LengthCheck.remark").set("maxLength", maxLength).format();
    }
}
