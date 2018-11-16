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
 * Defines the types or severities of {@link ProcessLog log entries}.
 */
public enum ProcessLogType {

    INFO, SUCCESS, WARNING, ERROR;

    @Override
    public String toString() {
        return NLS.get(ProcessLogType.class.getSimpleName() + "." + name());
    }
}
