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
 */
public class LengthCheck implements ValueCheck {

    private int maxLength;

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
        if (value.asString().length() > maxLength) {
            throw new IllegalArgumentException(NLS.fmtr("LengthCheck.errorMsg")
                                                  .set("length", value.asString().length())
                                                  .set("maxLength", maxLength)
                                                  .format());
        }
    }

    @Override
    public String generateRemark() {
        return NLS.fmtr("LengthCheck.remark").set("maxLength", maxLength).format();
    }
}
