/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Provides a parameter which accepts integer numbers.
 */
public class IntParameter extends TextParameter<Integer, IntParameter> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public IntParameter(String name, String label) {
        super(name, label);
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        Integer value = NLS.parseUserString(Integer.class, input.asString());
        return value != null ? String.valueOf(value) : null;
    }

    @Override
    protected Optional<Integer> resolveFromString(Value input) {
        return input.asOptionalInt();
    }
}
