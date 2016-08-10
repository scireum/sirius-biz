/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.jobs.ParameterHandler;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

@Register
public class StringParameterHandler implements ParameterHandler {

    @Override
    public Object convert(Value value) {
        return value.asString(null);
    }

    @Nonnull
    @Override
    public String getName() {
        return "string";
    }
}
