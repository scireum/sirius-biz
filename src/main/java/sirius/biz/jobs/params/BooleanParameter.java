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
 * Provides a checkbox parameter.
 */
public class BooleanParameter extends Parameter<Boolean, BooleanParameter> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public BooleanParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/selectBoolean.html.pasta";
    }

    /**
     * Checks and transforms the given value.
     * <p>
     * As a required boolean parameter has to be true we return null if the value is <tt>false</tt>. Later in the
     * check the value is empty and thus the check fails.
     *
     * @param input the input wrapped as <tt>Value</tt>
     * @return <tt>"true"</tt> if the value is <tt>true</tt>, <tt>null</tt> otherwise
     */
    @Override
    protected String checkAndTransformValue(Value input) {
        if (isRequired() && !input.asBoolean()) {
            return null;
        }

        return NLS.toMachineString(input.asBoolean());
    }

    @Override
    public BooleanParameter markRequired() {
        throw new UnsupportedOperationException(
                "A boolean parameter must not be marked as required as it is inherently so!");
    }

    @Override
    protected Optional<Boolean> resolveFromString(Value input) {
        return Optional.of(input.asBoolean());
    }
}
