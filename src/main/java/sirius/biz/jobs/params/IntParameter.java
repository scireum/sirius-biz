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
        Integer value = NLS.parseUserString(Integer.class, input.asString());
        return value != null ? String.valueOf(value) : null;
    }

    @Override
    protected Optional<Integer> resolveFromString(Value input) {
        return input.asOptionalInt();
    }
}
