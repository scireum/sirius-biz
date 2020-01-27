/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

/**
 * Enforces the presence of a value for the associated field.
 * <p>
 * Note, as most of the {@link Value} methods will perform an automatic <tt>trim</tt>, we also trim the contents before
 * checking for a non-empty value. (Thus " " would be considered empty and yield an error).
 * <p>
 * Use {@link #checkUntrimmed()} in order to suppress this behaviour.
 */
public class RequiredCheck implements ValueCheck {

    private boolean trim = true;

    @Override
    public void perform(Value value) {
        String effectiveValue = value.asString();

        if (trim) {
            effectiveValue = effectiveValue.trim();
        }

        if (Strings.isEmpty(effectiveValue)) {
            throw new IllegalArgumentException(NLS.get("RequiredCheck.errorMsg"));
        }
    }

    /**
     * Suppresses the automatic trim before checking if the given value is present.
     *
     * @return the check itself for fluent method calls
     */
    public RequiredCheck checkUntrimmed() {
        this.trim = false;
        return this;
    }

    @Override
    public String generateRemark() {
        return NLS.get("RequiredCheck.remark");
    }
}
