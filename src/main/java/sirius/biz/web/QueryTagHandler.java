/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.jdbc.Constraint;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.di.std.Named;

/**
 * Compiles a tag value provided by a {@link QueryTag} into a {@link Constraint}.
 */
public interface QueryTagHandler extends Named {

    /**
     * Computes the constraint.
     *
     * @param descriptor the entity for which the constraint is to be created
     * @param tagValue   the value to filter on
     * @return a constraint representing the given filter
     */
    Constraint generateConstraint(EntityDescriptor descriptor, String tagValue);
}
