/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.params;

import sirius.kernel.commons.Value;

public class StringParameter extends Parameter<StringParameter> {

    public StringParameter(String name, String title) {
        super(name, title);
    }

    @Override
    public String getTemplateName() {
        return "/templates/params/textfield.html.pasta";
    }

    @Override
    protected Object checkAndTransformValue(Value input) {
        return input.getString();
    }
}
