/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;

public interface ImportHandlerFactory {

    boolean accepts(Class<?> type);

    <E extends BaseEntity<?>> ImportHandler<E> create(Class<E> type, Importer importer, ImportContext context);
}
