/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.jobs.params;

import sirius.biz.jdbc.jobs.ParameterHandler;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

@Register
public class StringParameterHandler implements ParameterHandler {

    @Override
    public Object convert(Value value) {
        return value.getString();
    }

    @Nonnull
    @Override
    public String getName() {
        return "string";
    }
}
