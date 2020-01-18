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
 * Represents a plain string parameter.
 */
public class StringParameter extends TextParameter<String, StringParameter> {

    private boolean nullable = false;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public StringParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Marks the parameter as nullable.
     * <p>
     * When set, forces empty strings to be returned as {@code null} and makes it no longer required.
     *
     * @return the parameter itself for fluent method calls
     */
    public StringParameter markNullable() {
        this.nullable = true;
        this.required = false;
        return self();
    }

    /**
     * Marks the parameter as required and resets the nullable state if set.
     *
     * @return the parameter itself for fluent method calls
     */
    @Override
    public StringParameter markRequired() {
        this.nullable = false;
        return super.markRequired();
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (nullable && input.isEmptyString()) {
            return null;
        }
        return input.getString();
    }

    @Override
    protected Optional<String> resolveFromString(Value input) {
        return input.asOptionalString();
    }
}
