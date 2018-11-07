/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.params;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Optional;

public class IntParameter extends Parameter<Integer, IntParameter> {

    public IntParameter(String name, String title) {
        super(name, title);
    }

    @Override
    public String getTemplateName() {
        return "/templates/params/textfield.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return String.valueOf(NLS.parseUserString(int.class, input.asString()));
    }

    @Override
    protected Optional<Integer> resolveFromString(Value input) {
        return input.asOptionalInt();
    }
}
