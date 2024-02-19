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
 * Represents a check for a lookup value, mainly used to generate a remark.
 */
public class LookupValuesCheck implements ValueCheck {

    private final boolean allowCustomValues;

    /**
     * Creates a new check which will be used to validate a lookup value.
     *
     * @param allowCustomValues if true, custom values are allowed
     */
    public LookupValuesCheck(boolean allowCustomValues) {
        this.allowCustomValues = allowCustomValues;
    }

    @Override
    public void perform(Value value) {
        // Check will be performed by the LookupValuesTable
    }

    @Override
    public String generateRemark() {
        if (allowCustomValues) {
            return NLS.get("LookupValuesCheck.remark.allowCustomValues");
        }
        return NLS.get("LookupValuesCheck.remark");
    }
}
