/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Provides a parameter which accepts {@link Amount amounts}.
 */
public class AmountParameter extends TextParameter<Amount, AmountParameter> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public AmountParameter(String name, String label) {
        super(name, label);
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        Amount value = NLS.parseUserString(Amount.class, input.asString());
        return NLS.toMachineString(value);
    }

    @Override
    protected Optional<Amount> resolveFromString(Value input) {
        return Optional.of(input.getAmount());
    }
}
