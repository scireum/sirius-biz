/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.nls.NLS;

public enum ProcessState {

    STANDBY, RUNNING, CANCELED, TERMINATED;

    @Override
    public String toString() {
        return NLS.get(ProcessState.class.getSimpleName() + "." + name());
    }
}
