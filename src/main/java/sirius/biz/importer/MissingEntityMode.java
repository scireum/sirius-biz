/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

/**
 * Describes how an {@link BaseImportHandler} reacts if a missing entity is referenced in
 * {@link BaseImportHandler#parseComplexProperty(BaseEntity, Property, Value, Context)}.
 */
public enum MissingEntityMode {
    /**
     * Creates the missing entity based on the given data.
     */
    CREATE,

    /**
     * Ignores the reference and leaves the field empty.
     */
    IGNORE,

    /**
     * Fails with an appropriate error message.
     */
    FAIL;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
