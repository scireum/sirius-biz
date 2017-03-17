/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Constraint;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.di.std.Named;

/**
 * Created by aha on 27.01.17.
 */
public interface QueryTagHandler extends Named {

    Constraint generateConstraint(EntityDescriptor descriptor, String tagValue);

}
