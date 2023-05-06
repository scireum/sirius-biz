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

    private String defaultValue;

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
     * Specifies the default value to use.
     *
     * @param defaultValue the default value to use
     * @return the parameter itself for fluent method calls
     */
    public StringParameter withDefault(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        // We only assume the default value when no context was provided, represented by null data in Value.
        // An empty string means the parameter was explicitly submitted empty.
        if (input.getRawString() == null) {
            return defaultValue;
        }

        // Always return null when no default value was used and the input is empty or null.
        if (input.isEmptyString()) {
            return null;
        }

        return input.getString();
    }

    @Override
    protected Optional<String> resolveFromString(Value input) {
        return input.asOptionalString();
    }
}
