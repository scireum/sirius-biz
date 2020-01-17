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

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Provides a checkbox parameter.
 */
public class BooleanParameter extends Parameter<Boolean, BooleanParameter> {

    private boolean nullable = false;

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
        if (nullable && !resolveFromString(input).isPresent()) {
            return null;
        }
        return NLS.toMachineString(input.asBoolean());
    }

    /**
     * Marks the parameter as boolean.
     * <p>
     * This allows for a tri-state boolean where a job parameter can also receive
     * {@code null} when a value is not selected instead of being defaulted to false.
     *
     * @return the parameter itself for fluent method calls
     */
    public BooleanParameter markNullable() {
        this.nullable = true;
        return self();
    }

    @Override
    public BooleanParameter markRequired() {
        throw new UnsupportedOperationException(
                "A boolean parameter must not be marked as required as it is inherently so. Use markNullable() to make the parameter optional.");
    }

    @Override
    public boolean isRequired() {
        return !nullable;
    }

    @Override
    protected Optional<Boolean> resolveFromString(@Nonnull Value input) {
        if (nullable && !("true".equalsIgnoreCase(input.asString()) || "false".equalsIgnoreCase(input.asString()))) {
            return Optional.empty();
        }
        return Optional.of(input.asBoolean());
    }
}
