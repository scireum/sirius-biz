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
 * Enforces the presence of a value for the associated field.
 */
public class RequiredCheck implements ValueCheck {

    @Override
    public void perform(Value value) {
        if (value.isEmptyString()) {
            throw new IllegalArgumentException(NLS.get("RequiredCheck.errorMsg"));
        }
    }

    @Override
    public String generateRemark() {
        return NLS.get("RequiredCheck.remark");
    }
}
