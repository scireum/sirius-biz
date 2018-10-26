/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.nls.NLS;

public enum ProcessLogType {
    INFO, SUCCESS, WARNING, ERROR;

    @Override
    public String toString() {
        return NLS.get(ProcessLogType.class.getSimpleName() + "." + name());
    }
}
