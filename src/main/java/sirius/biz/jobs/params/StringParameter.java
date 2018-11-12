/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Value;

import java.util.Optional;

public class StringParameter extends Parameter<String,StringParameter> {

    public StringParameter(String name, String title) {
        super(name, title);
    }

    @Override
    public String getTemplateName() {
        return "/templates/params/textfield.html.pasta";
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
