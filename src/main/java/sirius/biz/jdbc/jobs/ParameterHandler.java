/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.jobs;

import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Named;

/**
 * Created by aha on 22.07.16.
 */
public interface ParameterHandler extends Named {
    Object convert(Value value);
}
