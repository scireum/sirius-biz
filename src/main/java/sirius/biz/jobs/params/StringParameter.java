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
public class StringParameter extends Parameter<String, StringParameter> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public StringParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public String getTemplateName() {
        return "/templates/jobs/params/textfield.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return input.getString();
    }

    @Override
    protected Optional<String> resolveFromString(Value input) {
        return input.asOptionalString();
    }
}
