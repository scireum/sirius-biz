/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Value;

import javax.annotation.Nullable;

/**
 * Represents a check which can be applied to a {@link FieldDefinition}.
 */
public interface ValueCheck {

    /**
     * Performs the check for the given value.
     *
     * @param value the value to check
     * @throws IllegalArgumentException if the value doesn't fulfill the check
     */
    void perform(Value value);

    /**
     * Generates a remark to be shown to the user.
     *
     * @return the remark to show or <tt>null</tt> to indicate that there is no remark.
     */
    @Nullable
    String generateRemark();
}
