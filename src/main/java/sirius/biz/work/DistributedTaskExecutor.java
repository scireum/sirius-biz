/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.work;

import sirius.kernel.di.std.Named;

public interface DistributedTaskExecutor extends Named {

    String getConcurrencyToken();
}
