/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.nls.NLS;

/**
 * Defines the states a {@link Process} can be in.
 */
public enum ProcessState {

    /**
     * A standby process exists in the background and is used from time to time (e.g. during API calls).
     */
    STANDBY,

    /**
     * Represents a process which is waiting for the execution to start.
     * <p>
     * This may be the case when the number of parallel processes is limited and the process is waiting for a slot.
     */
    WAITING,

    /**
     * Represents a process which is actively running.
     */
    RUNNING,

    /**
     * Represents a prcoess which is running but the user already tried to cancel it.
     */
    CANCELED,

    /**
     * Represents a process which has been terminated.
     */
    TERMINATED;

    @Override
    public String toString() {
        return NLS.get(ProcessState.class.getSimpleName() + "." + name());
    }
}
