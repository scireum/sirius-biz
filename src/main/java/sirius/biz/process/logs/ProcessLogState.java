/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.logs;

import sirius.kernel.nls.NLS;

/**
 * Defines the state for {@link ProcessLog log entries} which represent a task.
 */
public enum ProcessLogState {

    OPEN, RESOLVED, IGNORED;

    @Override
    public String toString() {
        return NLS.get(ProcessLogState.class.getSimpleName() + "." + name());
    }
}
