/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.kernel.nls.NLS;

/**
 * Declares the modes of operation for an {@link EntityImportJob}.
 */
public enum ImportMode {

    /**
     * Creates new entities and also updates existing ones.
     */
    NEW_AND_UPDATES,

    /**
     * Only updates existing entities.
     */
    UPDATE_ONLY,

    /**
     * ONly creates new entities.
     */
    NEW_ONLY;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
