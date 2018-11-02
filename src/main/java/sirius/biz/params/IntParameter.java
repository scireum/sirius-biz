/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.params;

import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

public class IntParameter extends Parameter<IntParameter> {

    public IntParameter(String name, String title) {
        super(name, title);
    }

    @Override
    public String getTemplateName() {
        return "/templates/params/textfield.html.pasta";
    }

    @Override
    protected Object checkAndTransformValue(Value input) {
            return NLS.parseUserString(int.class, input.asString());
    }
}
